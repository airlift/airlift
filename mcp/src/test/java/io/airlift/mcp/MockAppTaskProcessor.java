package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public class MockAppTaskProcessor
{
    private static final Logger log = Logger.get(MockAppTaskProcessor.class);

    private static final Duration TASK_POLL_INTERVAL = Duration.ofMillis(100);

    private final Optional<TaskController> taskController;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Map<TaskId, TaskEntry> tasks = new ConcurrentHashMap<>();

    private record PendingRequest(UUID requestId, String serverToClientMethod, Object serverToClientParam, Class<?> resultType, Consumer<? extends Optional<?>> handler)
    {
        private PendingRequest
        {
            requireNonNull(requestId, "requestId is null");
            requireNonNull(serverToClientParam, "serverToClientParam is null");
            requireNonNull(resultType, "resultType is null");
            requireNonNull(handler, "handler is null");
        }
    }

    private record TaskEntry(AtomicReference<PendingRequest> pendingRequest)
    {
        private TaskEntry
        {
            requireNonNull(pendingRequest, "pendingRequest is null");
        }
    }

    @Inject
    public MockAppTaskProcessor(Optional<TaskController> taskController, ObjectMapper objectMapper)
    {
        this.taskController = requireNonNull(taskController, "taskController is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        executorService = newVirtualThreadPerTaskExecutor();
    }

    @PostConstruct
    public void start()
    {
        if (taskController.isPresent()) {
            executorService.submit(this::processTasks);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        if (!shutdownAndAwaitTermination(executorService, 5, TimeUnit.SECONDS)) {
            log.warn("Executor thread did not terminate");
        }
    }

    public Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        Task task = requireTaskController().createTask(requestContext, callToolRequest);
        TaskId taskId = new TaskId(task.taskId());

        TaskEntry taskEntry = new TaskEntry(new AtomicReference<>());
        tasks.put(taskId, taskEntry);

        log.info("Created task %s", taskId);

        return task;
    }

    public void completeTask(TaskId taskId, CallToolResult result)
    {
        TaskController localTaskController = requireTaskController();

        localTaskController.setTaskResult(taskId, Optional.of(result));
        localTaskController.setTaskStatus(taskId, TaskStatus.COMPLETED, Optional.empty());
    }

    public <R, T> void setPendingRequest(TaskId taskId, String serverToClientMethod, T serverToClientParam, Class<R> resultType, Consumer<? extends Optional<R>> handler)
    {
        log.info("Setting pending request %s for task %s", serverToClientMethod, taskId);

        TaskEntry taskEntry = tasks.get(taskId);
        if (taskEntry != null) {
            PendingRequest pendingRequest = new PendingRequest(UUID.randomUUID(), serverToClientMethod, serverToClientParam, resultType, handler);
            if (!taskEntry.pendingRequest().compareAndSet(null, pendingRequest)) {
                throw new IllegalStateException("There is already a pending request for task with id " + taskId);
            }
        }
        else {
            throw new IllegalArgumentException("Task with id " + taskId + " does not exist");
        }
    }

    private void processTasks()
    {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TimeUnit.MILLISECONDS.sleep(TASK_POLL_INTERVAL.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            tasks.forEach(this::manageTask);
        }

        log.info("Exiting task processor thread");
    }

    private void manageTask(TaskId taskId, TaskEntry taskEntry)
    {
        requireTaskController().task(taskId).ifPresentOrElse(task -> manageTask(taskId, task, taskEntry),
                () -> tasks.remove(taskId));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void manageTask(TaskId taskId, Task task, TaskEntry taskEntry)
    {
        TaskController localTaskController = requireTaskController();

        PendingRequest pendingRequest = taskEntry.pendingRequest.get();
        if (pendingRequest != null) {
            if (task.status() == TaskStatus.WORKING) {
                log.info("Task %s changing to input required for %s", taskId, pendingRequest.serverToClientMethod);
                localTaskController.setTaskStatus(taskId, TaskStatus.INPUT_REQUIRED, Optional.empty());
                localTaskController.queueServerToClientMessage(taskId, Optional.of(pendingRequest.requestId), pendingRequest.serverToClientMethod(), Optional.of(pendingRequest.serverToClientParam()));
            }
            else if (task.status() == TaskStatus.INPUT_REQUIRED) {
                localTaskController.takeServerToClientResponse(taskId, pendingRequest.requestId, rpcResponse -> {
                    log.info("Task %s response received for request %s", taskId, pendingRequest.serverToClientMethod);

                    taskEntry.pendingRequest.set(null);
                    localTaskController.setTaskStatus(taskId, TaskStatus.WORKING, Optional.empty());

                    Optional<?> result = rpcResponse.map(objectMapper, pendingRequest.resultType);
                    ((Consumer) pendingRequest.handler).accept(result);
                });
            }
        }
    }

    private TaskController requireTaskController()
    {
        return taskController.orElseThrow(() -> new IllegalStateException("No task controller present"));
    }
}

package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.json.JsonModule;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskControllerModule;
import io.airlift.mcp.tasks.TaskFacade;
import io.airlift.mcp.tasks.Tasks;
import io.airlift.units.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.TaskStatus.CANCELLED;
import static io.airlift.mcp.model.TaskStatus.COMPLETED;
import static io.airlift.mcp.model.TaskStatus.FAILED;
import static io.airlift.mcp.model.TaskStatus.INPUT_REQUIRED;
import static io.airlift.mcp.model.TaskStatus.WORKING;
import static io.airlift.mcp.tasks.TaskConditions.hasMessage;
import static io.airlift.mcp.tasks.TaskConditions.isCompleted;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestTaskController
{
    private static final Duration TASK_TTL = new Duration(2, SECONDS);

    private final Closer closer = Closer.create();
    private final TaskController taskController;
    private final TestingDatabaseSessionController sessionController;

    public TestTaskController()
    {
        TestingDatabaseServer testingDatabaseServer = closer.register(new TestingDatabaseServer());

        McpConfig mcpConfig = new McpConfig()
                .setEventStreamingTimeout(new Duration(100, DAYS))
                .setEventStreamingPingThreshold(new Duration(1, MILLISECONDS))
                .setDefaultTaskTtl(TASK_TTL);

        Module module = binder -> {
            binder.install(new TaskControllerModule(subBinder -> subBinder.to(TestingTaskContextMapper.class).in(SINGLETON)));
            binder.bind(TestingDatabaseServer.class).toInstance(testingDatabaseServer);
            newOptionalBinder(binder, SessionController.class).setBinding().to(TestingDatabaseSessionController.class).in(SINGLETON);
            binder.bind(McpConfig.class).toInstance(mcpConfig);
            binder.bind(CancellationController.class).in(SINGLETON);
            binder.bind(McpCancellationHandler.class).toInstance(McpCancellationHandler.DEFAULT);
        };

        Injector injector = Guice.createInjector(module, new JsonModule());
        sessionController = injector.getInstance(TestingDatabaseSessionController.class);
        closer.register(sessionController::close);

        taskController = injector.getInstance(TaskController.class);
    }

    @BeforeAll
    public void setup()
    {
        sessionController.initialize();
    }

    @AfterAll
    public void tearDown()
            throws Exception
    {
        closer.close();
    }

    @Test
    public void testSimpleTaskTransitions()
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        TaskFacade task = tasks.createTask();
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        task = tasks.getTask(task.taskId()).orElseThrow();
        assertThat(task.completedAt()).isEmpty();
        assertThat(task.message()).isEmpty();

        JsonRpcMessage result = new JsonRpcResponse<>(task.requestId(), Optional.empty(), Optional.of(new CallToolResult(new TextContent("Chamming"))));
        tasks.setTaskMessage(task.taskId(), result, Optional.empty());

        task = tasks.getTask(task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);
        assertThat(task.completedAt()).isNotEmpty();
        assertThat(task.message()).isNotEmpty();

        tasks.deleteTask(task.taskId());
        assertThat(tasks.getTask(task.taskId())).isEmpty();
    }

    @Test
    public void testBlockUntilResult()
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        TaskFacade task = tasks.createTask();

        Future<TaskFacade> future = newVirtualThreadPerTaskExecutor().submit(() ->
                tasks.blockUntil(task.taskId(), java.time.Duration.ofDays(1), java.time.Duration.ofSeconds(1), hasMessage));

        assertThat(future).failsWithin(2, SECONDS);

        JsonRpcResponse<?> response = new JsonRpcResponse<>("bar", Optional.of(new JsonRpcErrorDetail(1, "hey", Optional.empty())), Optional.empty());
        tasks.completeTask(task.taskId(), response, Optional.empty());

        assertThat(future).succeedsWithin(5, SECONDS)
                .satisfies(completedTask -> {
                    assertThat(completedTask.toTaskStatus()).isEqualTo(FAILED);
                    assertThat(completedTask.message()).isPresent().get().isEqualTo(response);
                });
    }

    @Test
    public void testTaskResponses()
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        String id = UUID.randomUUID().toString();

        TaskFacade task = tasks.createTask();

        // simulate setting an outgoing request and then getting a response

        JsonRpcRequest<?> testRequest = JsonRpcRequest.buildRequest(id, "dummyMethod");

        tasks.setTaskMessage(task.taskId(), testRequest, Optional.empty());
        task = tasks.getTask(task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(INPUT_REQUIRED);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>(id, Optional.empty(), Optional.of("test response"));

        tasks.addServerToClientResponse(task.taskId(), testResponse);

        task = tasks.getTask(task.taskId()).orElseThrow();
        assertThat(task.responses()).hasSize(1);
        assertThat(task.responses().get(id)).isEqualTo(testResponse);
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);
    }

    @Test
    public void testTaskCleanup()
            throws InterruptedException
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        TaskFacade task = tasks.createTask();
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>("yo", Optional.empty(), Optional.of("test response"));
        tasks.setTaskMessage(task.taskId(), testResponse, Optional.empty());

        task = tasks.getTask(task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);

        Thread.sleep(TASK_TTL.toMillis() * 2);

        taskController.validateTaskContext(taskContextId);
        Optional<TaskFacade> shouldBeGone = tasks.getTask(task.taskId());
        assertThat(shouldBeGone).isEmpty();
    }

    @Test
    public void testDeleteTaskContext()
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        assertThat(taskController.validateTaskContext(taskContextId)).isTrue();

        tasks.createTask();
        tasks.createTask();
        tasks.createTask();

        assertThat(tasks.listTasks(100, Optional.empty())).hasSize(3);

        taskController.deleteTaskContext(taskContextId);

        assertThat(taskController.validateTaskContext(taskContextId)).isFalse();
        assertThat(tasks.listTasks(100, Optional.empty())).isEmpty();
    }

    @Test
    public void testTaskContextIsolation()
    {
        int taskQty = 100;

        TaskContextId taskContextId1 = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks1 = taskController.tasksForRequest(taskContextId1, "dummy", Optional.empty());
        TaskContextId taskContextId2 = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks2 = taskController.tasksForRequest(taskContextId2, "dummy", Optional.empty());

        Map<TaskContextId, List<TaskFacade>> tasksMap = IntStream.range(0, taskQty)
                .mapToObj(_ -> {
                    TaskContextId taskContextId = ThreadLocalRandom.current().nextBoolean() ? taskContextId1 : taskContextId2;
                    Tasks tasks = taskContextId.equals(taskContextId1) ? tasks1 : tasks2;
                    TaskFacade task = tasks.createTask();
                    return Map.entry(taskContextId, task);
                })
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, Collectors.toList())));

        assertThat(tasks1.listTasks(taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasksMap.getOrDefault(taskContextId1, ImmutableList.of()));
        assertThat(tasks2.listTasks(taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasksMap.getOrDefault(taskContextId2, ImmutableList.of()));
    }

    @Test
    public void testTaskCancellation()
            throws InterruptedException, TimeoutException
    {
        TaskContextId taskContextId = taskController.createTaskContext(authenticated("dummy"));
        Tasks tasks = taskController.tasksForRequest(taskContextId, "dummy", Optional.empty());

        TaskFacade task = tasks.createTask();

        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Future<?> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            tasks.executeCancellable(task.taskId(), () -> {
                try {
                    countDownLatch.await();
                }
                catch (InterruptedException e) {
                    cancelled.set(true);
                }
                return null;
            });
            return null;
        });

        tasks.requestTaskCancellation(task.taskId(), Optional.empty());
        tasks.blockUntil(task.taskId(), java.time.Duration.ofMinutes(1), java.time.Duration.ofSeconds(1), isCompleted);

        assertThat(future).succeedsWithin(5, SECONDS);
        assertThat(cancelled.get()).isTrue();

        TaskFacade cancelledTask = tasks.getTask(task.taskId()).orElseThrow();

        assertThat(cancelledTask.completedAt()).isPresent();

        assertThat(cancelledTask.toTaskStatus()).isEqualTo(CANCELLED);
    }
}

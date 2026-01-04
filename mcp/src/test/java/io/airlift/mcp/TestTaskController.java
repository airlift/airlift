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
import io.airlift.mcp.tasks.NewTask;
import io.airlift.mcp.tasks.TaskAdapter;
import io.airlift.mcp.tasks.TaskContextController;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskControllerModule;
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
    private final TaskContextController taskContextController;
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
        taskContextController = injector.getInstance(TaskContextController.class);
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
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testSimpleTaskTransitions").build());
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.completedAt()).isEmpty();
        assertThat(task.message()).isEmpty();

        JsonRpcMessage result = new JsonRpcResponse<>(task.requestId(), Optional.empty(), Optional.of(new CallToolResult(new TextContent("Chamming"))));
        taskController.setTaskMessage(taskContextId, task.taskId(), result, Optional.empty());

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);
        assertThat(task.completedAt()).isNotEmpty();
        assertThat(task.message()).isNotEmpty();

        taskController.deleteTask(taskContextId, task.taskId());
        assertThat(taskController.getTask(taskContextId, task.taskId())).isEmpty();
    }

    @Test
    public void testBlockUntilResult()
    {
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testBlockUntilResult").build());

        Future<TaskAdapter> future = newVirtualThreadPerTaskExecutor().submit(() ->
                taskController.blockUntilResponse(taskContextId, task.taskId(), java.time.Duration.ofDays(1), java.time.Duration.ofSeconds(1)));

        assertThat(future).failsWithin(2, SECONDS);

        JsonRpcResponse<?> response = new JsonRpcResponse<>("bar", Optional.of(new JsonRpcErrorDetail(1, "hey", Optional.empty())), Optional.empty());
        taskController.completeTask(taskContextId, task.taskId(), response, Optional.empty());

        assertThat(future).succeedsWithin(5, SECONDS)
                .satisfies(completedTask -> {
                    assertThat(completedTask.toTaskStatus()).isEqualTo(FAILED);
                    assertThat(completedTask.message()).isPresent().get().isEqualTo(response);
                });
    }

    @Test
    public void testTaskResponses()
    {
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        String id = UUID.randomUUID().toString();

        TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testTaskResponses").build());

        // simulate setting an outgoing request and then getting a response

        JsonRpcRequest<?> testRequest = JsonRpcRequest.buildRequest(id, "dummyMethod");

        taskController.setTaskMessage(taskContextId, task.taskId(), testRequest, Optional.empty());
        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(INPUT_REQUIRED);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>(id, Optional.empty(), Optional.of("test response"));

        taskController.addServerToClientResponse(taskContextId, task.taskId(), testResponse);

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.responses()).hasSize(1);
        assertThat(task.responses().get(id)).isEqualTo(testResponse);
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);
    }

    @Test
    public void testTaskCleanup()
            throws InterruptedException
    {
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testTaskCleanup").build());
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>("yo", Optional.empty(), Optional.of("test response"));
        taskController.setTaskMessage(taskContextId, task.taskId(), testResponse, Optional.empty());

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);

        Thread.sleep(TASK_TTL.toMillis() * 2);

        taskContextController.validateTaskContext(taskContextId);
        Optional<TaskAdapter> shouldBeGone = taskController.getTask(taskContextId, task.taskId());
        assertThat(shouldBeGone).isEmpty();
    }

    @Test
    public void testDeleteTaskContext()
    {
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        assertThat(taskContextController.validateTaskContext(taskContextId)).isTrue();

        taskController.createTask(NewTask.builder(taskContextId, "testDeleteTaskContext1").build());
        taskController.createTask(NewTask.builder(taskContextId, "testDeleteTaskContext2").build());
        taskController.createTask(NewTask.builder(taskContextId, "testDeleteTaskContext3").build());

        assertThat(taskController.listTasks(taskContextId, 100, Optional.empty())).hasSize(3);

        taskContextController.deleteTaskContext(taskContextId);

        assertThat(taskContextController.validateTaskContext(taskContextId)).isFalse();
        assertThat(taskController.listTasks(taskContextId, 100, Optional.empty())).isEmpty();
    }

    @Test
    public void testTaskContextIsolation()
    {
        int taskQty = 100;

        TaskContextId taskContextId1 = taskContextController.createTaskContext(authenticated("dummy"));
        TaskContextId taskContextId2 = taskContextController.createTaskContext(authenticated("dummy"));

        Map<TaskContextId, List<TaskAdapter>> tasks = IntStream.range(0, taskQty)
                .mapToObj(i -> {
                    TaskContextId taskContextId = ThreadLocalRandom.current().nextBoolean() ? taskContextId1 : taskContextId2;
                    TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testTaskContextIsolation" + i).build());
                    return Map.entry(taskContextId, task);
                })
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, Collectors.toList())));

        assertThat(taskController.listTasks(taskContextId1, taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasks.getOrDefault(taskContextId1, ImmutableList.of()));
        assertThat(taskController.listTasks(taskContextId2, taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasks.getOrDefault(taskContextId2, ImmutableList.of()));
    }

    @Test
    public void testTaskCancellation()
            throws InterruptedException, TimeoutException
    {
        TaskContextId taskContextId = taskContextController.createTaskContext(authenticated("dummy"));

        TaskAdapter task = taskController.createTask(NewTask.builder(taskContextId, "testTaskCancellation").build());

        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Future<?> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            taskController.executeCancellable(taskContextId, task.taskId(), () -> {
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

        taskController.requestTaskCancellation(taskContextId, task.taskId(), Optional.empty());
        taskController.blockUntilCompleted(taskContextId, task.taskId(), java.time.Duration.ofMinutes(1), java.time.Duration.ofSeconds(1));

        assertThat(future).succeedsWithin(5, SECONDS);
        assertThat(cancelled.get()).isTrue();

        TaskAdapter cancelledTask = taskController.getTask(taskContextId, task.taskId()).orElseThrow();

        assertThat(cancelledTask.completedAt()).isPresent();

        assertThat(cancelledTask.toTaskStatus()).isEqualTo(CANCELLED);
    }
}

package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import io.airlift.mcp.tasks.TaskAdapter;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.units.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.mcp.model.TaskStatus.COMPLETED;
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
    private static final Duration TASK_TTL = new Duration(2, TimeUnit.SECONDS);

    private final Closer closer = Closer.create();
    private final TaskController taskController;
    private final TestingDatabaseSessionController sessionController;

    public TestTaskController()
    {
        McpConfig mcpConfig = new McpConfig()
                .setEventStreamingTimeout(new Duration(100, DAYS))
                .setEventStreamingPingThreshold(new Duration(1, MILLISECONDS))
                .setDefaultTaskTtl(TASK_TTL)
                .setTaskCleanupInterval(new Duration(1, SECONDS));

        Module module = binder -> {
            binder.bind(TestingDatabaseServer.class).in(SINGLETON);
            newOptionalBinder(binder, SessionController.class).setBinding().to(TestingDatabaseSessionController.class).in(SINGLETON);
            binder.bind(McpConfig.class).toInstance(mcpConfig);
            binder.bind(TaskController.class).in(SINGLETON);
        };

        Injector injector = Guice.createInjector(module, new JsonModule());
        closer.register(injector.getInstance(TestingDatabaseServer.class));
        sessionController = injector.getInstance(TestingDatabaseSessionController.class);

        taskController = injector.getInstance(TaskController.class);

        closer.register(taskController::stop);
    }

    @BeforeAll
    public void setup()
    {
        sessionController.initialize();
        taskController.start();
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
        TaskContextId taskContextId = newTaskContextId();

        TaskAdapter task = taskController.createTask(taskContextId, "foo", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.completeAt()).isEmpty();
        assertThat(task.message()).isEmpty();

        JsonRpcMessage result = McpTasks.buildResult(task, new CallToolResult(new TextContent("Chamming")));
        boolean success = taskController.setTaskMessage(taskContextId, task.taskId(), Optional.of(result), Optional.empty(), false);
        assertThat(success).isTrue();

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);
        assertThat(task.completeAt()).isNotEmpty();
        assertThat(task.message()).isNotEmpty();

        taskController.deleteTask(taskContextId, task.taskId());
        assertThat(taskController.getTask(taskContextId, task.taskId())).isEmpty();
    }

    @Test
    public void testBlockUntilResult()
    {
        TaskContextId taskContextId = newTaskContextId();

        TaskAdapter task = taskController.createTask(taskContextId, "bar", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());

        Future<TaskAdapter> future = newVirtualThreadPerTaskExecutor().submit(() ->
                taskController.blockUntilCondition(taskContextId, task.taskId(), java.time.Duration.ofDays(1), java.time.Duration.ofMillis(1), () -> {}, taskAdapter ->
                        taskAdapter.completeAt().isPresent()));

        assertThat(future).failsWithin(2, SECONDS);

        JsonRpcResponse<?> response = new JsonRpcResponse<>("bar", Optional.of(new JsonRpcErrorDetail(1, "hey", Optional.empty())), Optional.empty());
        taskController.setTaskMessage(taskContextId, task.taskId(), Optional.of(response), Optional.empty(), false);

        assertThat(future).succeedsWithin(5, SECONDS);
    }

    @Test
    public void testTaskResponses()
    {
        TaskContextId taskContextId = newTaskContextId();

        String id = UUID.randomUUID().toString();

        TaskAdapter task = taskController.createTask(taskContextId, "baz", ImmutableMap.of("hi", "there"), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());

        // simulate setting an outgoing request and then getting a response

        JsonRpcRequest<?> testRequest = JsonRpcRequest.buildRequest(id, "dummyMethod");

        taskController.setTaskMessage(taskContextId, task.taskId(), Optional.of(testRequest), Optional.empty(), false);
        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(INPUT_REQUIRED);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>(id, Optional.empty(), Optional.of("test response"));

        taskController.addTaskResponse(taskContextId, task.taskId(), testResponse);

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.responses()).hasSize(1);
        assertThat(task.responses().get(id)).isEqualTo(testResponse);
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);
    }

    @Test
    public void testTaskCleanup()
            throws InterruptedException
    {
        TaskContextId taskContextId = newTaskContextId();

        TaskAdapter task = taskController.createTask(taskContextId, "boing", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
        assertThat(task.toTaskStatus()).isEqualTo(WORKING);

        JsonRpcResponse<String> testResponse = new JsonRpcResponse<>("yo", Optional.empty(), Optional.of("test response"));
        taskController.setTaskMessage(taskContextId, task.taskId(), Optional.of(testResponse), Optional.empty(), false);

        task = taskController.getTask(taskContextId, task.taskId()).orElseThrow();
        assertThat(task.toTaskStatus()).isEqualTo(COMPLETED);

        Thread.sleep(TASK_TTL.toMillis() * 2);

        Optional<TaskAdapter> shouldBeGone = taskController.getTask(taskContextId, task.taskId());
        assertThat(shouldBeGone).isEmpty();
    }

    @Test
    public void testDeleteTaskContext()
    {
        TaskContextId taskContextId = newTaskContextId();
        assertThat(taskController.validateTaskContextId(taskContextId)).isTrue();

        taskController.createTask(taskContextId, "1", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
        taskController.createTask(taskContextId, "2", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
        taskController.createTask(taskContextId, "3", ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());

        assertThat(taskController.listTasks(taskContextId, 100, Optional.empty())).hasSize(3);

        taskController.deleteTaskContext(taskContextId);

        assertThat(taskController.validateTaskContextId(taskContextId)).isFalse();
        assertThat(taskController.listTasks(taskContextId, 100, Optional.empty())).isEmpty();
    }

    @Test
    public void testTaskContextIsolation()
    {
        int taskQty = 100;

        TaskContextId taskContextId1 = newTaskContextId();
        TaskContextId taskContextId2 = newTaskContextId();

        Map<TaskContextId, List<TaskAdapter>> tasks = IntStream.range(0, taskQty)
                .mapToObj(i -> {
                    TaskContextId taskContextId = ThreadLocalRandom.current().nextBoolean() ? taskContextId1 : taskContextId2;
                    TaskAdapter task = taskController.createTask(taskContextId, i, ImmutableMap.of(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
                    return Map.entry(taskContextId, task);
                })
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, Collectors.toList())));

        assertThat(taskController.listTasks(taskContextId1, taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasks.getOrDefault(taskContextId1, ImmutableList.of()));
        assertThat(taskController.listTasks(taskContextId2, taskQty, Optional.empty()))
                .containsExactlyInAnyOrderElementsOf(tasks.getOrDefault(taskContextId2, ImmutableList.of()));
    }

    private TaskContextId newTaskContextId()
    {
        // the implementations we've bound don't use the HttpServletRequest arg
        return taskController.newTaskContextId(null);
    }
}

package io.airlift.mcp.tasks.memory;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskResult.Suspended;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.tasks.TaskExecutor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMemoryTaskEngine
{
    private static final McpRequestContext CALLER = new TestingRequestContext(authenticated("test-caller"));
    private static final McpRequestContext OTHER_CALLER = new TestingRequestContext(authenticated("other-caller"));
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, TaskExecutor> handlers = new ConcurrentHashMap<>();
    private final TaskExecutor executor = (identity, callToolRequest, taskContext) -> handlers.get(callToolRequest.name()).runTask(identity, callToolRequest, taskContext);

    private MemoryTaskEngine engine;

    @BeforeEach
    public void setUp()
    {
        engine = new MemoryTaskEngine();
        engine.start();
    }

    @AfterEach
    public void tearDown()
    {
        engine.close();
        handlers.clear();
    }

    @Test
    public void testCompletedTask()
    {
        CountDownLatch release = new CountDownLatch(1);
        Task handle = createTask((_, _, taskContext) -> {
            taskContext.setStatusMessage("working on it");
            release.await();
            return new CallToolResult(new TextContent("done"));
        });

        assertThat(handle.status()).isEqualTo(TaskStatus.WORKING);
        assertThat(handle.ttlMs()).hasValue(60_000);
        assertThat(handle.pollIntervalMs()).hasValue(500);
        assertThat(handle.result()).isEmpty();

        waitFor(() -> getTask(handle).statusMessage().isPresent());
        assertThat(getTask(handle).statusMessage()).contains("working on it");

        release.countDown();
        waitFor(() -> getTask(handle).status() == TaskStatus.COMPLETED);

        Task task = getTask(handle);
        assertThat(task.result().orElseThrow().content()).contains(List.of(new TextContent("done")));
        assertThat(task.error()).isEmpty();
    }

    @Test
    public void testToolErrorCompletesTheTask()
    {
        // the Tasks extension reserves "failed" for JSON-RPC errors - a tool error is a completed task
        Task handle = createTask((_, _, _) -> new CallToolResult(List.of(new TextContent("nope")), Optional.empty(), true));

        waitFor(() -> getTask(handle).status().isTerminal());

        Task task = getTask(handle);
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.result().orElseThrow().isError()).contains(true);
    }

    @Test
    public void testProtocolErrorFailsTheTask()
    {
        Task handle = createTask((_, _, _) -> {
            throw exception(INTERNAL_ERROR, "did not work");
        });

        waitFor(() -> getTask(handle).status().isTerminal());

        Task task = getTask(handle);
        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.error()).contains(new JsonRpcErrorDetail(INTERNAL_ERROR, "did not work"));
        assertThat(task.statusMessage()).contains("did not work");
        assertThat(task.result()).isEmpty();
    }

    @Test
    public void testInputRequests()
    {
        Task handle = createTask((_, _, taskContext) -> {
            Map<String, Object> inputResponses = taskContext.awaitInputResponses(inputRequests("first", "second"), TIMEOUT);
            return new CallToolResult(new TextContent(inputResponses.toString()));
        });

        waitFor(() -> getTask(handle).status() == TaskStatus.INPUT_REQUIRED);
        assertThat(getTask(handle).inputRequests().orElseThrow()).containsOnlyKeys("first", "second");

        // a partial update leaves the task parked on the requests that are still outstanding
        assertThat(engine.updateTask(handle.taskId(), ImmutableMap.of("first", "one", "unknown", "ignored"))).isTrue();
        Task task = getTask(handle);
        assertThat(task.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(task.inputRequests().orElseThrow()).containsOnlyKeys("second");

        assertThat(engine.updateTask(handle.taskId(), ImmutableMap.of("second", "two"))).isTrue();
        waitFor(() -> getTask(handle).status().isTerminal());

        Task completed = getTask(handle);
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.inputRequests()).isEmpty();
        assertThat(completed.result().orElseThrow().content().orElseThrow()).containsExactly(new TextContent("{first=one, second=two}"));
    }

    @Test
    public void testInputRequestsWithoutBlocking()
    {
        // the executor asks for input, suspends, and is run again once the answer arrives
        Task handle = createTask((_, _, taskContext) -> {
            Map<String, Object> inputResponses = taskContext.inputResponses();
            if (inputResponses.isEmpty()) {
                taskContext.requestInput(inputRequests("first"));
                return Suspended.INSTANCE;
            }
            return new CallToolResult(new TextContent(inputResponses.toString()));
        });

        waitFor(() -> getTask(handle).status() == TaskStatus.INPUT_REQUIRED);
        assertThat(getTask(handle).inputRequests().orElseThrow()).containsOnlyKeys("first");

        assertThat(engine.updateTask(handle.taskId(), ImmutableMap.of("first", "one"))).isTrue();
        waitFor(() -> getTask(handle).status().isTerminal());

        Task completed = getTask(handle);
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.result().orElseThrow().content().orElseThrow()).containsExactly(new TextContent("{first=one}"));
    }

    @Test
    public void testCancellation()
    {
        Task handle = createTask((_, _, _) -> {
            Thread.sleep(TIMEOUT);
            return new CallToolResult(new TextContent("not reached"));
        });

        assertThat(engine.cancelTask(handle.taskId())).isTrue();
        waitFor(() -> getTask(handle).status().isTerminal());
        assertThat(getTask(handle).status()).isEqualTo(TaskStatus.CANCELLED);

        // cancelling a terminal task is accepted and does nothing
        assertThat(engine.cancelTask(handle.taskId())).isTrue();
        assertThat(getTask(handle).status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    public void testCancellationWhileWaitingForInput()
    {
        Task handle = createTask((_, _, taskContext) -> {
            taskContext.awaitInputResponses(inputRequests("first"), TIMEOUT);
            return new CallToolResult(new TextContent("not reached"));
        });

        waitFor(() -> getTask(handle).status() == TaskStatus.INPUT_REQUIRED);
        assertThat(engine.cancelTask(handle.taskId())).isTrue();

        waitFor(() -> getTask(handle).status().isTerminal());
        assertThat(getTask(handle).status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    public void testUnknownTask()
    {
        assertThat(engine.getTask("does-not-exist")).isEmpty();
        assertThat(engine.updateTask("does-not-exist", ImmutableMap.of())).isFalse();
        assertThat(engine.cancelTask("does-not-exist")).isFalse();
        assertThat(engine.validateTaskAccess(CALLER, "does-not-exist")).isFalse();
    }

    @Test
    public void testTaskAccess()
    {
        Task handle = createTask((_, _, _) -> new CallToolResult(new TextContent("done")));

        assertThat(engine.validateTaskAccess(CALLER, handle.taskId())).isTrue();
        // another caller's task is indistinguishable from a task that does not exist
        assertThat(engine.validateTaskAccess(OTHER_CALLER, handle.taskId())).isFalse();
    }

    @Test
    public void testExpiredTask()
    {
        Task handle = createTask((_, _, _) -> new CallToolResult(new TextContent("done")), Duration.ofMillis(1));

        waitFor(() -> engine.getTask(handle.taskId()).isEmpty());
        assertThatThrownBy(() -> getTask(handle)).isInstanceOf(NoSuchElementException.class);
    }

    private Task createTask(TaskExecutor handler)
    {
        return createTask(handler, Duration.ofSeconds(60));
    }

    private Task createTask(TaskExecutor handler, Duration ttl)
    {
        String toolName = "test_" + handlers.size();
        handlers.put(toolName, handler);

        Task task = engine.createTask(CALLER, new CallToolRequest(toolName, ImmutableMap.of()), ttl, Duration.ofMillis(500));
        engine.executeTask(task.taskId(), executor);
        return task;
    }

    private Task getTask(Task handle)
    {
        return engine.getTask(handle.taskId()).orElseThrow();
    }

    private static Map<String, InputRequest> inputRequests(String... keys)
    {
        return Arrays.stream(keys)
                .collect(toImmutableMap(key -> key, key -> new InputRequest(METHOD_ELICITATION_CREATE, ImmutableMap.of("message", key))));
    }

    private record TestingRequestContext(Authenticated<?> identity)
            implements McpRequestContext
    {
        @Override
        public HttpServletRequest request()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientCapabilities clientCapabilities()
        {
            return ClientCapabilities.EMPTY;
        }

        @Override
        public void sendProgress(double progress, double total, String message) {}

        @Override
        public void sendMessage(String method, Optional<Object> params) {}

        @Override
        public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data) {}
    }

    private static void waitFor(BooleanSupplier condition)
    {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Condition was not met within " + TIMEOUT);
    }
}

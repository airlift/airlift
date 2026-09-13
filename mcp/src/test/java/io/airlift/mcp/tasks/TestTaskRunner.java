package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskResult;
import io.airlift.mcp.reflection.MethodInvoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpException.nonResultException;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.CallToolResult.errorResult;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestTaskRunner
{
    private static final CallToolRequest CALL_TOOL_REQUEST = new CallToolRequest("test");
    private static final String TASK_ID = "task-1";
    private static final String GENERIC_MESSAGE = "Failed to run task " + TASK_ID;
    private static final TaskResult GENERIC_FAILURE = new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, GENERIC_MESSAGE));
    private static final TaskResult INTERRUPTED = new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "Task execution was interrupted"));
    private static final TaskResult INPUT_REQUIRED_RESULT = new TaskResult.Failed(new JsonRpcErrorDetail(
            INTERNAL_ERROR,
            "A task executor must ask for input with TaskContext.requestInput or awaitInputResponses, not return an input-required result"));

    @AfterEach
    public void clearInterrupt()
    {
        // the runner restores the interrupt on the calling thread, which is the test thread here
        Thread.interrupted();
    }

    @Test
    public void testErrorFailsTheTask()
    {
        // as on the synchronous path, an Error's message does not reach the client
        assertThat(run(new AssertionError("boom"), false)).isEqualTo(GENERIC_FAILURE);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testErrorWithoutMessageFailsTheTask()
    {
        assertThat(run(new StackOverflowError(), false)).isEqualTo(GENERIC_FAILURE);
    }

    @Test
    public void testCheckedExceptionFailsTheTaskWithoutItsMessage()
    {
        String marker = "connect to db-7.internal:5432 failed";
        TaskResult result = run(new IOException(marker), false);

        // the engines report the error's message as the task's status message too
        assertThat(result).isEqualTo(GENERIC_FAILURE);
        assertThat(((TaskResult.Failed) result).error().message()).doesNotContain(marker);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testUncheckedExceptionCompletesWithItsMessage()
    {
        // as on the synchronous path, the exception's message is the tool's error result
        assertThat(run(new IllegalStateException("tool state is invalid"), false)).isEqualTo(errorResult("tool state is invalid"));
    }

    @Test
    public void testUncheckedExceptionWithoutMessageCompletesTheTask()
    {
        assertThat(run(new IllegalStateException(), false)).isEqualTo(errorResult("Internal error"));
    }

    @Test
    public void testUncheckedExceptionIsJudgedByItsRootCause()
    {
        // as on the synchronous path, a checked exception keeps its message to itself however it is wrapped
        assertThat(run(new UncheckedIOException("tool backend failed", new IOException("connect to db-7.internal:5432 failed")), false))
                .isEqualTo(GENERIC_FAILURE);
    }

    @Test
    public void testMcpExceptionCompletesWithAnErrorResult()
    {
        assertThat(run(exception(INTERNAL_ERROR, "did not work"), false)).isEqualTo(errorResult("did not work"));
        assertThat(run(exception(INTERNAL_ERROR, "did not work").asClientException(), false)).isEqualTo(errorResult("did not work"));
    }

    @Test
    public void testSelfContainedMcpExceptionFailsTheTask()
    {
        assertThat(run(nonResultException(INVALID_PARAMS, "bad arguments"), false))
                .isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(INVALID_PARAMS, "bad arguments")));
    }

    @Test
    public void testExceptionAfterCancellationCancelsTheTask()
    {
        assertThat(run(new IllegalStateException("tool state is invalid"), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(run(new IOException("connect failed"), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(run(nonResultException(INVALID_PARAMS, "bad arguments"), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("toolFailures")
    public void testSettlesAsASynchronousCallReports(Throwable failure)
            throws ReflectiveOperationException
    {
        // the synchronous path: the tool's method is invoked through MethodInvoker, and tools/call answers an
        // McpClientException with its error result and any other exception with a JSON-RPC error
        ThrowingTool tool = new ThrowingTool(failure);
        MethodInvoker methodInvoker = new MethodInvoker(() -> tool, ThrowingTool.class.getMethod("call"), ImmutableList.of(), JsonMapper.builder().build());
        Throwable thrown = catchThrowable(() -> methodInvoker.builder(mock(McpRequestContext.class)).invoke());

        TaskResult taskResult = run(failure, false);
        switch (thrown) {
            case McpClientException clientException -> assertThat(taskResult).isEqualTo(CallToolResult.forError(clientException));
            case McpException mcpException when mcpException.isSelfContained() -> assertThat(taskResult).isEqualTo(new TaskResult.Failed(mcpException.errorDetail()));
            // a generic internal error - each path names what failed in its own way
            case McpException mcpException -> assertThat(taskResult).isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(mcpException.errorDetail().code(), GENERIC_MESSAGE, Optional.empty())));
            default -> throw new AssertionError("unexpected synchronous outcome", thrown);
        }
    }

    public static List<Throwable> toolFailures()
    {
        return ImmutableList.of(
                new IllegalStateException("tool state is invalid"),
                new IllegalStateException(),
                exception(INTERNAL_ERROR, "did not work"),
                exception(INTERNAL_ERROR, "did not work").asClientException(),
                nonResultException(INVALID_PARAMS, "bad arguments"),
                new IOException("connect failed"),
                new UncheckedIOException("tool backend failed", new IOException("connect failed")),
                new AssertionError("boom"));
    }

    @Test
    public void testNullResultFailsTheTask()
    {
        assertThat(run(null)).isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "executor returned a null result")));
    }

    @Test
    public void testErrorAfterCancellationCancelsTheTask()
    {
        assertThat(run(new AssertionError("boom"), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
    }

    @Test
    public void testInterruptedException()
    {
        assertThat(run(new InterruptedException(), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testWrappedInterruptedException()
    {
        assertThat(run(new RuntimeException(new InterruptedException()), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testInterruptedIo()
    {
        assertThat(run(new InterruptedIOException(), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
        assertThat(run(new ClosedByInterruptException(), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testSocketTimeoutIsNotAnInterruption()
            throws IOException
    {
        // a read timeout is an InterruptedIOException, but nothing interrupted the thread
        SocketTimeoutException timeout = readTimeout();
        assertThat(run(timeout, false)).isEqualTo(GENERIC_FAILURE);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        assertThat(run(new UncheckedIOException("tool backend timed out", timeout), false)).isEqualTo(GENERIC_FAILURE);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testWrappedInterruptAfterCancellationCancelsTheTask()
    {
        assertThat(run(new RuntimeException(new InterruptedException()), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testInterruptWrappedInMcpExceptionAfterCancellationCancelsTheTask()
    {
        assertThat(run(exception(INTERNAL_ERROR, new InterruptedException()), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testInterruptWrappedInMcpClientExceptionAfterCancellationCancelsTheTask()
    {
        assertThat(run(exception(INTERNAL_ERROR, new InterruptedException()).asClientException(), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testInterruptWrappedInMcpException()
    {
        assertThat(run(exception(INTERNAL_ERROR, new InterruptedException()), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testInterruptWrappedInMcpClientException()
    {
        assertThat(run(exception(INTERNAL_ERROR, new InterruptedException()).asClientException(), false)).isEqualTo(INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testCyclicCauseIsNotAnInterruption()
    {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        // a chain with no root is judged by the exception thrown
        assertThat(run(first, false)).isEqualTo(errorResult("first"));
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testInputRequiredResultFailsTheTask()
    {
        TaskResult inputRequired = CallToolResult.inputRequestsBuilder()
                .add("approval", METHOD_ELICITATION_CREATE, ImmutableMap.of())
                .withRequestState("state")
                .build();
        assertThat(run(inputRequired)).isEqualTo(INPUT_REQUIRED_RESULT);

        // request state alone still asks the client to come back with answers
        TaskResult requestStateOnly = new CallToolResult(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("state"), Optional.empty(), Optional.empty());
        assertThat(run(requestStateOnly)).isEqualTo(INPUT_REQUIRED_RESULT);
    }

    @Test
    public void testToolResultsSettleAsReturned()
    {
        TaskResult completed = new CallToolResult(new TextContent("done"));
        assertThat(run(completed)).isEqualTo(completed);

        // the Tasks extension reserves "failed" for JSON-RPC errors - a tool error still completes the task
        TaskResult toolError = CallToolResult.errorResult("nope");
        assertThat(run(toolError)).isEqualTo(toolError);
    }

    private static TaskResult run(TaskResult result)
    {
        return TaskRunner.run((_, _, _) -> result, authenticated("caller"), CALL_TOOL_REQUEST, mock(TaskContext.class));
    }

    public static class ThrowingTool
    {
        private final Throwable failure;

        public ThrowingTool(Throwable failure)
        {
            this.failure = failure;
        }

        public void call()
                throws Throwable
        {
            throw failure;
        }
    }

    private static SocketTimeoutException readTimeout()
            throws IOException
    {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket socket = new Socket(server.getInetAddress(), server.getLocalPort())) {
            socket.setSoTimeout(1);
            socket.getInputStream().read();
        }
        catch (SocketTimeoutException e) {
            return e;
        }
        throw new AssertionError("read did not time out");
    }

    private static TaskResult run(Throwable throwable, boolean cancellationRequested)
    {
        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.isCancellationRequested()).thenReturn(cancellationRequested);
        when(taskContext.taskId()).thenReturn(TASK_ID);

        return TaskRunner.run((_, _, _) -> throwChecked(throwable), authenticated("caller"), CALL_TOOL_REQUEST, taskContext);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> TaskResult throwChecked(Throwable throwable)
            throws T
    {
        throw (T) throwable;
    }
}

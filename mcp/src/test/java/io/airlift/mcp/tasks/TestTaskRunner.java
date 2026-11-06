package io.airlift.mcp.tasks;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestTaskRunner
{
    private static final CallToolRequest CALL_TOOL_REQUEST = new CallToolRequest("test");
    private static final TaskResult INTERRUPTED = new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "Task execution was interrupted"));

    @AfterEach
    public void clearInterrupt()
    {
        // the runner restores the interrupt on the calling thread, which is the test thread here
        Thread.interrupted();
    }

    @Test
    public void testErrorFailsTheTask()
    {
        assertThat(run(new AssertionError("boom"), false))
                .isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "boom")));
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testErrorWithoutMessageFailsTheTask()
    {
        assertThat(run(new StackOverflowError(), false))
                .isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "Internal error")));
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
    public void testWrappedInterruptAfterCancellationCancelsTheTask()
    {
        assertThat(run(new RuntimeException(new InterruptedException()), true)).isEqualTo(TaskResult.Cancelled.INSTANCE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void testCyclicCauseIsNotAnInterruption()
    {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(run(first, false)).isEqualTo(new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "first")));
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private static TaskResult run(Throwable throwable, boolean cancellationRequested)
    {
        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.isCancellationRequested()).thenReturn(cancellationRequested);

        return TaskRunner.run((_, _, _) -> throwChecked(throwable), authenticated("caller"), CALL_TOOL_REQUEST, taskContext);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> TaskResult throwChecked(Throwable throwable)
            throws T
    {
        throw (T) throwable;
    }
}

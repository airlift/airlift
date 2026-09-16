package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.tasks.TaskExecutor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface McpRequestContext
{
    HttpServletRequest request();

    Authenticated<?> identity();

    void sendProgress(double progress, double total, String message);

    void sendMessage(String method, Optional<Object> params);

    ClientCapabilities clientCapabilities();

    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data);

    /**
     * Creates a task for the tool call being handled, returning what the tool returns to the
     * client. The task is not run: how and when it runs is the caller's choice - see
     * {@link McpTasks#executeTask} to run it here.
     * <p>
     * The tool must be declared with a {@link io.airlift.mcp.model.TaskSupport} other than
     * {@link io.airlift.mcp.model.TaskSupport#NONE}.
     * <p>
     * Contexts that cannot create tasks - a task already executing, or a legacy protocol - throw.
     */
    default Task createTask(CallToolRequest callToolRequest)
    {
        throw new UnsupportedOperationException("Tasks cannot be created in this context");
    }

    /**
     * Convenience method that creates a task and immediately runs it, returning what the tool returns to the client.
     * The task is run with the given executor via an internal thread.
     * <p>
     * Contexts that cannot create tasks - a task already executing, or a legacy protocol - throw.
     */
    default Task createAndExecuteTask(CallToolRequest callToolRequest, TaskExecutor executor)
    {
        throw new UnsupportedOperationException("Tasks cannot be created in this context");
    }
}

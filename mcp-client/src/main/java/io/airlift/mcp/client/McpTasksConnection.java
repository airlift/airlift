package io.airlift.mcp.client;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

public interface McpTasksConnection
        extends McpConnection
{
    @Override
    <V> McpTasksConnection withSetting(McpConnectionSetting<V> setting, V value);

    ToolResult callToolOrTask(CallToolRequest callToolRequest);

    ToolResult getTask(GetTaskRequest request);

    void cancelTask(String taskId);

    void updateTask(UpdateTaskRequest request);

    void sleepTask(Task task)
            throws InterruptedException;
}

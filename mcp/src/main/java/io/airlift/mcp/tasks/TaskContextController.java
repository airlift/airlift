package io.airlift.mcp.tasks;

import io.airlift.mcp.McpIdentity;

public interface TaskContextController
{
    TaskContextId createTaskContext(McpIdentity identity);

    boolean validateTaskContext(TaskContextId taskContextId);

    void deleteTaskContext(TaskContextId taskContextId);
}

package io.airlift.mcp;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.tasks.TaskContextController;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskContextMapper;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;

class TestingTaskContextMapper
        implements TaskContextMapper
{
    private final Supplier<TaskContextId> taskContextIdSupplier;

    @Inject
    TestingTaskContextMapper(TaskContextController taskContextController)
    {
        taskContextIdSupplier = Suppliers.memoize(() -> taskContextController.createTaskContext(authenticated("testing")));
    }

    @Override
    public TaskContextId map(McpIdentity identity, SessionId sessionId)
    {
        return taskContextIdSupplier.get();
    }
}

package io.airlift.mcp.tasks;

import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.sessions.SessionId;

public interface TaskContextMapper
{
    TaskContextMapper FROM_SESSION = (_, sessionId) -> sessionId;

    TaskContextId map(McpIdentity identity, SessionId sessionId);
}

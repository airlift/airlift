package io.airlift.mcp;

import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.sessions.SessionId;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface McpIdentityMapper
{
    /**
     * Map the request and optional session ID to an McpIdentity. If {@code sessionId} is present,
     * the identity should be associated with that session and the session should be validated.
     */
    McpIdentity map(HttpServletRequest request, Optional<SessionId> sessionId);
}

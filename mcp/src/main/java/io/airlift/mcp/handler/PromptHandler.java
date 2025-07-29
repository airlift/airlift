package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

public interface PromptHandler
{
    GetPromptResult getPrompt(Request request, SessionId sessionId, McpNotifier notifier, GetPromptRequest getPromptRequest);
}

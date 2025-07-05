package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import jakarta.ws.rs.core.Request;

public interface PromptHandler
{
    GetPromptResult getPrompt(Request request, McpNotifier notifier, GetPromptRequest getPromptRequest);
}

package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;

public interface PromptHandler
{
    GetPromptResult getPrompt(RequestContext requestContext, McpNotifier notifier, GetPromptRequest getPromptRequest);
}

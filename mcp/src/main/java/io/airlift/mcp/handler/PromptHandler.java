package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;

public interface PromptHandler
{
    GetPromptResult getPrompt(McpRequestContext requestContext, GetPromptRequest getPromptRequest);
}

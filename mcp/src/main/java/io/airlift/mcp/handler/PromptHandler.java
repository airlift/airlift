package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResponse;

public interface PromptHandler
{
    GetPromptResponse getPrompt(McpRequestContext requestContext, GetPromptRequest getPromptRequest);
}

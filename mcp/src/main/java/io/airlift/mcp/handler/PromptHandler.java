package io.airlift.mcp.handler;

import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import jakarta.servlet.http.HttpServletRequest;

public interface PromptHandler
{
    GetPromptResult getPrompt(HttpServletRequest request, GetPromptRequest getPromptRequest);
}

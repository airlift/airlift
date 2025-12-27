package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;

public interface CompletionHandler
{
    CompleteResult complete(McpRequestContext requestContext, CompleteRequest completeRequest);
}

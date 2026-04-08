package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;

public interface ResourceHandler
{
    ReadResourceResult readResource(McpRequestContext requestContext, Resource sourceResource, ReadResourceRequest readResourceRequest, boolean allowIncompleteResult);
}

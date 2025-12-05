package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;

import java.util.List;

public interface ResourceHandler
{
    List<ResourceContents> readResource(McpRequestContext requestContext, Resource sourceResource, ReadResourceRequest readResourceRequest);
}

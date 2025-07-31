package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;

import java.util.List;

public interface ResourceHandler
{
    List<ResourceContents> readResource(RequestContext requestContext, McpNotifier notifier, Resource sourceResource, ReadResourceRequest readResourceRequest);
}

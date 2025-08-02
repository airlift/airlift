package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;

public interface ListResourcesHandler
{
    ResourcesEntry listResources(RequestContext requestContext, McpNotifier notifier);
}

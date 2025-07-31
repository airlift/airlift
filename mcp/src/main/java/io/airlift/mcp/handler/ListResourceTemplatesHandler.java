package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;

public interface ListResourceTemplatesHandler
{
    ResourceTemplatesEntry listResourceTemplates(RequestContext requestContext, McpNotifier notifier);
}

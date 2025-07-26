package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import jakarta.ws.rs.core.Request;

public interface ListResourceTemplatesHandler
{
    ResourceTemplatesEntry listResourceTemplates(Request request, McpNotifier notifier);
}

package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;

import java.util.List;
import java.util.Map;

public interface ReadResourceTemplateHandler
{
    List<ResourceContents> readResource(RequestContext requestContext, McpNotifier notifier, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, Map<String, String> pathTemplateValues);
}

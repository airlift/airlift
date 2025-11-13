package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;

import java.util.List;

public interface ResourceTemplateHandler
{
    List<ResourceContents> readResourceTemplate(McpRequestContext requestContext, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, ResourceTemplateValues resourceTemplateValues);
}

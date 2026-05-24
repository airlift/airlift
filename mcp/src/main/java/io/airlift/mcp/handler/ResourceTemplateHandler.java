package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;

public interface ResourceTemplateHandler
{
    ReadResourceResult readResourceTemplate(McpRequestContext requestContext, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, ResourceTemplateValues resourceTemplateValues);
}

package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResponse;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;

public interface ResourceTemplateHandler
{
    ReadResourceResponse readResourceTemplate(McpRequestContext requestContext, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, ResourceTemplateValues resourceTemplateValues, boolean allowIncompleteResult);
}

package io.airlift.mcp.handler;

import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface ResourceTemplateHandler
{
    List<ResourceContents> readResourceTemplate(HttpServletRequest request, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest);
}

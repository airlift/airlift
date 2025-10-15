package io.airlift.mcp.handler;

import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface ResourceHandler {
    List<ResourceContents> readResource(
            HttpServletRequest request, Resource sourceResource, ReadResourceRequest readResourceRequest);
}

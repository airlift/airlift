package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;

public interface McpMetadataMapper
{
    McpMetadata map(HttpServletRequest request);
}

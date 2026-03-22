package io.airlift.mcp.legacy;

import io.airlift.mcp.McpRequestContext;

public interface LegacyEventStreaming
{
    void handleEventStreaming(McpRequestContext requestContext);

    void checkSaveSentMessages(McpRequestContext requestContext);
}

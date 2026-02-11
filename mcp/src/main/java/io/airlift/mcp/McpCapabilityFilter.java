package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

/**
 * Filter for controlling access to MCP capabilities based on authenticated identity.
 */
public interface McpCapabilityFilter
{
    boolean isAllowed(Authenticated<?> identity, Tool tool);

    boolean isAllowed(Authenticated<?> identity, Prompt prompt);

    boolean isAllowed(Authenticated<?> identity, Resource resource);

    boolean isAllowed(Authenticated<?> identity, ResourceTemplate resourceTemplate);

    boolean isAllowed(Authenticated<?> identity, CompleteReference reference);

    boolean isAllowed(Authenticated<?> identity, String resourceUri);
}

package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.List;

/**
 * Filter for controlling access to MCP capabilities based on authenticated identity.
 */
public interface McpCapabilityFilter
{
    List<Tool> filterTools(Authenticated<?> identity, List<Tool> tools);

    List<Prompt> filterPrompts(Authenticated<?> identity, List<Prompt> prompts);

    List<Resource> filterResources(Authenticated<?> identity, List<Resource> resources);

    List<ResourceTemplate> filterResourceTemplates(Authenticated<?> identity, List<ResourceTemplate> resourceTemplates);

    List<CompleteReference> filterCompletions(Authenticated<?> identity, List<CompleteReference> references);
}

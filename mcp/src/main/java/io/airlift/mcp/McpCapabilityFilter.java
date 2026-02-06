package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

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

    default List<Tool> allowedTools(Authenticated<?> identity, List<Tool> tools)
    {
        return tools.stream()
                .filter(tool -> isAllowed(identity, tool))
                .collect(toImmutableList());
    }

    default List<Prompt> allowedPrompts(Authenticated<?> identity, List<Prompt> prompts)
    {
        return prompts.stream()
                .filter(prompt -> isAllowed(identity, prompt))
                .collect(toImmutableList());
    }

    default List<Resource> allowedResources(Authenticated<?> identity, List<Resource> resources)
    {
        return resources.stream()
                .filter(resource -> isAllowed(identity, resource))
                .collect(toImmutableList());
    }

    default List<ResourceTemplate> allowedResourceTemplates(Authenticated<?> identity, List<ResourceTemplate> resourceTemplates)
    {
        return resourceTemplates.stream()
                .filter(resourceTemplate -> isAllowed(identity, resourceTemplate))
                .collect(toImmutableList());
    }
}

package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.List;

class AllowAllCapabilityFilter
        implements McpCapabilityFilter
{
    @Override
    public List<Tool> filterTools(Authenticated<?> identity, List<Tool> tools)
    {
        return ImmutableList.copyOf(tools);
    }

    @Override
    public List<Prompt> filterPrompts(Authenticated<?> identity, List<Prompt> prompts)
    {
        return ImmutableList.copyOf(prompts);
    }

    @Override
    public List<Resource> filterResources(Authenticated<?> identity, List<Resource> resources)
    {
        return ImmutableList.copyOf(resources);
    }

    @Override
    public List<ResourceTemplate> filterResourceTemplates(Authenticated<?> identity, List<ResourceTemplate> resourceTemplates)
    {
        return ImmutableList.copyOf(resourceTemplates);
    }

    @Override
    public List<CompleteReference> filterCompletions(Authenticated<?> identity, List<CompleteReference> completions)
    {
        return ImmutableList.copyOf(completions);
    }
}

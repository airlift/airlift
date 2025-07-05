package io.airlift.mcp.model;

import java.util.List;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = List.copyOf(resourceTemplates);
    }
}

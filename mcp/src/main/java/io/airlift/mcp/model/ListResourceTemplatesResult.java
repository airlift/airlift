package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
    }
}

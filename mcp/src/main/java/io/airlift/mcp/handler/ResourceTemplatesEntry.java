package io.airlift.mcp.handler;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.ResourceTemplate;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record ResourceTemplatesEntry(List<ResourceTemplate> resourceTemplates, ReadResourceTemplateHandler handler)
{
    public ResourceTemplatesEntry
    {
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
        requireNonNull(handler, "handler is null");
    }
}

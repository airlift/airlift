package io.airlift.mcp.handler;

import io.airlift.mcp.model.ResourceTemplate;

import static java.util.Objects.requireNonNull;

public record ResourceTemplateEntry(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
{
    public ResourceTemplateEntry
    {
        requireNonNull(resourceTemplate, "resourceTemplate is null");
        requireNonNull(handler, "handler is null");
    }
}

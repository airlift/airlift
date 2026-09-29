package io.airlift.mcp.handler;

import io.airlift.mcp.model.ResourceTemplate;

import static java.util.Objects.requireNonNull;

public record ResourceTemplateEntry(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler, ResourceKind kind)
{
    public ResourceTemplateEntry
    {
        requireNonNull(resourceTemplate, "resourceTemplate is null");
        requireNonNull(handler, "handler is null");
        requireNonNull(kind, "kind is null");
    }

    public boolean isSkill()
    {
        return kind.isSkill();
    }
}

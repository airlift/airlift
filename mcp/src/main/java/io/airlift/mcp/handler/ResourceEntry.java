package io.airlift.mcp.handler;

import io.airlift.mcp.model.Resource;

import static java.util.Objects.requireNonNull;

public record ResourceEntry(Resource resource, ResourceHandler handler, boolean isSkill)
{
    public ResourceEntry
    {
        requireNonNull(resource, "resource is null");
        requireNonNull(handler, "handler is null");
    }
}

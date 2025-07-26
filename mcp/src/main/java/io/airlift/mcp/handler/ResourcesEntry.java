package io.airlift.mcp.handler;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.Resource;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record ResourcesEntry(List<Resource> resources, ReadResourceHandler handler)
{
    public ResourcesEntry
    {
        resources = ImmutableList.copyOf(resources);
        requireNonNull(handler, "handler is null");
    }
}

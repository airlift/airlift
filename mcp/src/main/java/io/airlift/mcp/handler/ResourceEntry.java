package io.airlift.mcp.handler;

import static java.util.Objects.requireNonNull;

import io.airlift.mcp.model.Resource;

public record ResourceEntry(Resource resource, ResourceHandler handler) {
    public ResourceEntry {
        requireNonNull(resource, "resource is null");
        requireNonNull(handler, "handler is null");
    }
}

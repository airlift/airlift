package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListRootsRequest(Optional<Map<String, Object>> meta)
        implements Meta, InputRequest
{
    public ListRootsRequest
    {
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public Object withMeta(Map<String, Object> meta)
    {
        return new ListRootsRequest(Optional.of(meta));
    }
}

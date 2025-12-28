package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta, OptionalInt ttl)
        implements Meta, TaskMetadata
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(arguments);
        meta = firstNonNull(meta, Optional.empty());
        ttl = firstNonNull(ttl, OptionalInt.empty());
    }

    public CallToolRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty(), OptionalInt.empty());
    }
}

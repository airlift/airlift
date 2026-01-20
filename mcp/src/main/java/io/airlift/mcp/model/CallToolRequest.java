package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta)
        implements Meta
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = firstNonNull(arguments, ImmutableMap.of());
        meta = firstNonNull(meta, Optional.empty());
    }

    public CallToolRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty());
    }

    public CallToolRequest(String name)
    {
        this(name, ImmutableMap.of(), Optional.empty());
    }
}

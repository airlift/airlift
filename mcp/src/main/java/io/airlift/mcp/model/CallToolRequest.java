package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta)
        implements Meta
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(arguments);
    }

    public CallToolRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty());
    }
}

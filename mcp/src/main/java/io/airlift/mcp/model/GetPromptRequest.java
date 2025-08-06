package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record GetPromptRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta)
        implements Meta
{
    public GetPromptRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(arguments);
        meta = firstNonNull(meta, Optional.empty());
    }

    public GetPromptRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty());
    }
}

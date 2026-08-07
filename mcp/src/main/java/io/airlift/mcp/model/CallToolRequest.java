package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<String> requestState, Optional<Map<String, Object>> inputResponses, Optional<Map<String, Object>> meta)
        implements Meta, InputResponses
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = requireNonNullElse(arguments, ImmutableMap.of());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputResponses = requireNonNullElse(inputResponses, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public CallToolRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public CallToolRequest(String name)
    {
        this(name, ImmutableMap.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public CallToolRequest withMeta(Map<String, Object> meta)
    {
        return new CallToolRequest(name, arguments, requestState, inputResponses, Optional.of(meta));
    }

    public CallToolRequest withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses)
    {
        return new CallToolRequest(name, arguments, requestState, Optional.of(inputResponses), meta);
    }
}

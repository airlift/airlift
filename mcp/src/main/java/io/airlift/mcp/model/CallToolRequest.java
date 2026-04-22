package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<Map<String, InputResponse>> inputResponses, Optional<String> requestState, Optional<Map<String, Object>> meta)
        implements Meta, InputResponsable<CallToolRequest>
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = requireNonNullElse(arguments, ImmutableMap.of());
        inputResponses = requireNonNullElse(inputResponses, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
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
    public CallToolRequest withInputResponses(InputResponses inputResponses)
    {
        return new CallToolRequest(name, arguments, Optional.of(inputResponses.inputResponses()), inputResponses.requestState(), meta);
    }

    @Override
    public CallToolRequest withMeta(Map<String, Object> meta)
    {
        return new CallToolRequest(name, arguments, inputResponses, requestState, Optional.of(meta));
    }
}

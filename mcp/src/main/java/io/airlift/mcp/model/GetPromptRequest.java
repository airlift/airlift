package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptRequest(String name, Map<String, Object> arguments, Optional<Map<String, InputResponse>> inputResponses, Optional<String> requestState, Optional<Map<String, Object>> meta)
        implements Meta, InputResponsable<GetPromptRequest>
{
    public GetPromptRequest
    {
        requireNonNull(name, "name is null");
        arguments = requireNonNullElse(arguments, ImmutableMap.of());
        inputResponses = requireNonNullElse(inputResponses, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public GetPromptRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public GetPromptRequest withInputResponses(InputResponses inputResponses)
    {
        return new GetPromptRequest(name, arguments, Optional.of(inputResponses.inputResponses()), inputResponses.requestState(), meta);
    }

    @Override
    public GetPromptRequest withMeta(Map<String, Object> meta)
    {
        return new GetPromptRequest(name, arguments, inputResponses, requestState, Optional.of(meta));
    }
}

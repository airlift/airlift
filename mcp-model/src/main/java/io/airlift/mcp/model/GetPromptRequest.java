package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptRequest(String name, Map<String, Object> arguments, Optional<String> requestState, Optional<Map<String, Object>> inputResponses, Optional<Map<String, Object>> meta)
        implements Meta<GetPromptRequest>, InputResponses<GetPromptRequest>
{
    public GetPromptRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(requireNonNullElse(arguments, ImmutableMap.of()));
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputResponses = requireNonNullElse(inputResponses, Optional.empty());
        meta = normalize(meta);
    }

    public GetPromptRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty());
    }

    public GetPromptRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta)
    {
        this(name, arguments, Optional.empty(), Optional.empty(), meta);
    }

    @Override
    public GetPromptRequest withMeta(Map<String, Object> meta)
    {
        return new GetPromptRequest(name, arguments, requestState, inputResponses, Optional.of(meta));
    }

    @Override
    public GetPromptRequest withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses)
    {
        return new GetPromptRequest(name, arguments, requestState, Optional.of(inputResponses), meta);
    }
}

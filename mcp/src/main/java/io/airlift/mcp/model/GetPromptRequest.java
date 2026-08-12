package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta)
        implements Meta<GetPromptRequest>
{
    public GetPromptRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(requireNonNullElse(arguments, ImmutableMap.of()));
        meta = normalize(meta);
    }

    public GetPromptRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty());
    }

    @Override
    public GetPromptRequest withMeta(Map<String, Object> meta)
    {
        return new GetPromptRequest(name, arguments, Optional.of(meta));
    }
}

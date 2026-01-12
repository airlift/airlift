package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record SubscribeRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta
{
    public SubscribeRequest
    {
        requireNonNull(uri, "uri is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public SubscribeRequest withMeta(Map<String, Object> meta)
    {
        return new SubscribeRequest(uri, Optional.of(meta));
    }
}

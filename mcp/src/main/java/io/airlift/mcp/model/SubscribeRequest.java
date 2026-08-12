package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record SubscribeRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta<SubscribeRequest>
{
    public SubscribeRequest
    {
        requireNonNull(uri, "uri is null");
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public SubscribeRequest withMeta(Map<String, Object> meta)
    {
        return new SubscribeRequest(uri, Optional.of(meta));
    }
}

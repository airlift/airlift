package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record SubscribeRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta<SubscribeRequest>
{
    public SubscribeRequest
    {
        requireNonNull(uri, "uri is null");
        meta = normalize(meta);
    }

    @Override
    public SubscribeRequest withMeta(Map<String, Object> meta)
    {
        return new SubscribeRequest(uri, Optional.of(meta));
    }
}

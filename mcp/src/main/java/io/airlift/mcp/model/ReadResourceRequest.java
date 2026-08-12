package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record ReadResourceRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta<ReadResourceRequest>
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        meta = normalize(meta);
    }

    public ReadResourceRequest(String uri)
    {
        this(uri, Optional.empty());
    }

    @Override
    public ReadResourceRequest withMeta(Map<String, Object> meta)
    {
        return new ReadResourceRequest(uri, Optional.of(meta));
    }
}

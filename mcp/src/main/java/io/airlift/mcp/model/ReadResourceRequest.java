package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        meta = requireNonNullElse(meta, Optional.empty());
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

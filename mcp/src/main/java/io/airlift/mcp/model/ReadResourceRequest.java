package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ReadResourceRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        meta = firstNonNull(meta, Optional.empty());
    }

    public ReadResourceRequest(String uri)
    {
        this(uri, Optional.empty());
    }
}

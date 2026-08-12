package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta<ReadResourceRequest>
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
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

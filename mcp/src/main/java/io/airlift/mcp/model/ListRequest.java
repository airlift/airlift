package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListRequest(Optional<String> cursor, Optional<Map<String, Object>> meta)
        implements PaginatedRequest, Meta<ListRequest>
{
    public ListRequest
    {
        cursor = requireNonNullElse(cursor, Optional.empty());
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public ListRequest withMeta(Map<String, Object> meta)
    {
        return new ListRequest(cursor, Optional.of(meta));
    }
}

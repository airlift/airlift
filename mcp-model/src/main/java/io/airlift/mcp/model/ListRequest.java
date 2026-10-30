package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListRequest(Optional<String> cursor, Optional<Map<String, Object>> meta)
        implements PaginatedRequest, Meta<ListRequest>
{
    public ListRequest
    {
        cursor = requireNonNullElse(cursor, Optional.empty());
        meta = normalize(meta);
    }

    @Override
    public ListRequest withMeta(Map<String, Object> meta)
    {
        return new ListRequest(cursor, Optional.of(meta));
    }
}

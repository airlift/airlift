package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record ListRequest(Optional<String> cursor)
        implements PaginatedRequest
{
    public ListRequest
    {
        cursor = firstNonNull(cursor, Optional.empty());
    }
}

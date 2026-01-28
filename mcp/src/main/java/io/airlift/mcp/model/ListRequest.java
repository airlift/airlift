package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListRequest(Optional<String> cursor)
        implements PaginatedRequest
{
    public ListRequest
    {
        cursor = requireNonNullElse(cursor, Optional.empty());
    }
}

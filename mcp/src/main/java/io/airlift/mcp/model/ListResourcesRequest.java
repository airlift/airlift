package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListResourcesRequest(Optional<String> cursor)
        implements Pagination
{
    public ListResourcesRequest
    {
        requireNonNull(cursor, "nextCursor is null");
    }
}

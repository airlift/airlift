package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListResourceTemplatesRequest(Optional<String> cursor)
        implements Pagination
{
    public ListResourceTemplatesRequest
    {
        requireNonNull(cursor, "nextCursor is null");
    }
}

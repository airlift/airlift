package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListToolsRequest(Optional<String> cursor)
        implements Pagination
{
    public ListToolsRequest
    {
        requireNonNull(cursor, "nextCursor is null");
    }
}

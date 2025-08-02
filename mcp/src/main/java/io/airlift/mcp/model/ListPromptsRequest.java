package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListPromptsRequest(Optional<String> cursor)
        implements Pagination
{
    public ListPromptsRequest
    {
        requireNonNull(cursor, "nextCursor is null");
    }
}

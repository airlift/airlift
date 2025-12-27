package io.airlift.mcp.model;

import java.util.Optional;

public interface PaginatedResult
{
    Optional<String> nextCursor();
}

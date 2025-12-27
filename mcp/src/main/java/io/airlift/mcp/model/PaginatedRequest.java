package io.airlift.mcp.model;

import java.util.Optional;

public interface PaginatedRequest
{
    Optional<String> cursor();
}

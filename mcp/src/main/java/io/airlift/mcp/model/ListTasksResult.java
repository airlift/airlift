package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListTasksResult(List<Task> tasks, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListTasksResult
    {
        tasks = ImmutableList.copyOf(tasks);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
    }
}

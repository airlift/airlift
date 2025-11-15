package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListRootsResult(List<Root> roots, Optional<String> nextCursor)
{
    public ListRootsResult
    {
        roots = ImmutableList.copyOf(roots);
        requireNonNull(nextCursor, "nextCursor is null");
    }

    public record Root(String uri, String name)
    {
        public Root
        {
            requireNonNull(uri, "uri is null");
            requireNonNull(name, "name is null");
        }
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListRootsResult(List<Root> roots, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ListRootsResult
    {
        roots = ImmutableList.copyOf(roots);
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public ListRootsResult withMeta(Map<String, Object> meta)
    {
        return new ListRootsResult(roots, Optional.of(meta));
    }
}

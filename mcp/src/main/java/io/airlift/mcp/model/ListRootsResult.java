package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListRootsResult(List<Root> roots, Optional<Map<String, Object>> meta)
        implements Meta<ListRootsResult>
{
    public ListRootsResult
    {
        roots = ImmutableList.copyOf(roots);
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public ListRootsResult withMeta(Map<String, Object> meta)
    {
        return new ListRootsResult(roots, Optional.of(meta));
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record ListRootsResult(List<Root> roots, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ListRootsResult
    {
        roots = ImmutableList.copyOf(roots);
        meta = firstNonNull(meta, Optional.empty());
    }
}

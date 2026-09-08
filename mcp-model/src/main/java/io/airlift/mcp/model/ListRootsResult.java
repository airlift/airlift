package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;

public record ListRootsResult(List<Root> roots, Optional<Map<String, Object>> meta)
        implements Meta<ListRootsResult>
{
    public ListRootsResult
    {
        roots = ImmutableList.copyOf(roots);
        meta = normalize(meta);
    }

    @Override
    public ListRootsResult withMeta(Map<String, Object> meta)
    {
        return new ListRootsResult(roots, Optional.of(meta));
    }
}

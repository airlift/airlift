package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceDirectoryResult(List<Resource> resources, Optional<String> nextCursor, Optional<Map<String, Object>> meta)
        implements PaginatedResult, Meta<ReadResourceDirectoryResult>
{
    public ReadResourceDirectoryResult
    {
        resources = ImmutableList.copyOf(resources);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        meta = normalize(meta);
    }

    public ReadResourceDirectoryResult(List<Resource> resources, Optional<String> nextCursor)
    {
        this(resources, nextCursor, Optional.empty());
    }

    @Override
    public ReadResourceDirectoryResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceDirectoryResult(resources, nextCursor, Optional.of(meta));
    }
}

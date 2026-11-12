package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListSkillsResult(List<Skill> skills, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ListSkillsResult>,
                   Meta<ListSkillsResult>,
                   PaginatedResult
{
    public ListSkillsResult
    {
        skills = ImmutableList.copyOf(skills);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ListSkillsResult(List<Skill> skills, Optional<String> nextCursor)
    {
        this(skills, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListSkillsResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListSkillsResult(skills, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListSkillsResult withMeta(Map<String, Object> meta)
    {
        return new ListSkillsResult(skills, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}

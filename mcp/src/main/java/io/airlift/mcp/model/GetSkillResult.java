package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetSkillResult(Skill skill, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<GetSkillResult>,
                   Meta<GetSkillResult>
{
    public GetSkillResult
    {
        requireNonNull(skill, "skill is null");
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public GetSkillResult(Skill skill)
    {
        this(skill, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public GetSkillResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new GetSkillResult(skill, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public GetSkillResult withMeta(Map<String, Object> meta)
    {
        return new GetSkillResult(skill, ttlMs, cacheScope, Optional.of(meta));
    }
}

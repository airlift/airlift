package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record GetSkillRequest(String uri, Optional<Map<String, Object>> meta)
        implements Meta<GetSkillRequest>
{
    public GetSkillRequest
    {
        requireNonNull(uri, "uri is null");
        meta = normalize(meta);
    }

    public GetSkillRequest(String uri)
    {
        this(uri, Optional.empty());
    }

    @Override
    public GetSkillRequest withMeta(Map<String, Object> meta)
    {
        return new GetSkillRequest(uri, Optional.of(meta));
    }
}

package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record SetLevelRequest(LoggingLevel level, Optional<Map<String, Object>> meta)
        implements Meta<SetLevelRequest>
{
    public SetLevelRequest
    {
        requireNonNull(level, "level is null");
        meta = normalize(meta);
    }

    @Override
    public SetLevelRequest withMeta(Map<String, Object> meta)
    {
        return new SetLevelRequest(level, Optional.ofNullable(meta));
    }
}

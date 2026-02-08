package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Root(String uri, Optional<String> name, Optional<Map<String, Object>> meta)
        implements Meta
{
    public Root
    {
        requireNonNull(uri, "uri is null");
        name = requireNonNullElse(name, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public Root withMeta(Map<String, Object> meta)
    {
        return new Root(uri, name, Optional.of(meta));
    }
}

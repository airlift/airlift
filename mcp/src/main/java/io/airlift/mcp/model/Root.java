package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Root(String uri, Optional<String> name, Optional<Map<String, Object>> meta)
        implements Meta
{
    public Root
    {
        requireNonNull(uri, "uri is null");
        name = firstNonNull(name, Optional.empty());
        meta = firstNonNull(meta, Optional.empty());
    }
}

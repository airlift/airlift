package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ElicitRequestUrl(Optional<String> mode, String elicitationId, String message, String url, Optional<Map<String, Object>> meta, OptionalInt ttl)
        implements Meta, TaskMetadata
{
    public ElicitRequestUrl
    {
        mode = requireNonNullElse(mode, Optional.empty());
        requireNonNull(elicitationId, "elicitationId is null");
        requireNonNull(message, "message is null");
        requireNonNull(url, "url is null");
        meta = requireNonNullElse(meta, Optional.empty());
        ttl = requireNonNullElse(ttl, OptionalInt.empty());
    }

    public ElicitRequestUrl(String elicitationId, String message, String url)
    {
        this(Optional.of("url"), elicitationId, message, url, Optional.empty(), OptionalInt.empty());
    }

    @Override
    public ElicitRequestUrl withMeta(Map<String, Object> meta)
    {
        return new ElicitRequestUrl(mode, elicitationId, message, url, Optional.of(meta), ttl);
    }
}

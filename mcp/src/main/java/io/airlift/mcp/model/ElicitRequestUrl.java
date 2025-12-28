package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ElicitRequestUrl(Optional<String> mode, String elicitationId, String message, String url, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitRequestUrl
    {
        mode = firstNonNull(mode, Optional.empty());
        requireNonNull(elicitationId, "elicitationId is null");
        requireNonNull(message, "message is null");
        requireNonNull(url, "url is null");
        meta = firstNonNull(meta, Optional.empty());
    }

    public ElicitRequestUrl(String elicitationId, String message, String url)
    {
        this(Optional.of("url"), elicitationId, message, url, Optional.empty());
    }
}

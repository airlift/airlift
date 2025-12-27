package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record ElicitResult(Action action, Optional<Map<String, Object>> content, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitResult
    {
        requireNonNull(action, "action is null");
        content = firstNonNull(content, Optional.empty());
        meta = firstNonNull(meta, Optional.empty());
    }

    public enum Action
    {
        ACCEPT,
        DECLINE,
        CANCEL;

        @JsonValue
        public String toJsonValue()
        {
            return name().toLowerCase(ROOT);
        }
    }
}

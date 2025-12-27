package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record ElicitResult(Action action, Optional<Map<String, Object>> content, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitResult
    {
        requireNonNull(action, "action is null");
        requireNonNull(content, "content is null");
        requireNonNull(meta, "meta is null");
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

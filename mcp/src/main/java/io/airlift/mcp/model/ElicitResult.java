package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ElicitResult(Action action, Optional<Map<String, Object>> content, Optional<Map<String, Object>> meta)
        implements Meta
{
    public ElicitResult
    {
        requireNonNull(action, "action is null");
        content = requireNonNullElse(content, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public ElicitResult withMeta(Map<String, Object> meta)
    {
        return new ElicitResult(action, content, Optional.of(meta));
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

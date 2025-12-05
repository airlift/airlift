package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record ElicitResult(Action action, Optional<Map<String, Object>> content)
{
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

    public ElicitResult
    {
        requireNonNull(action, "action is null");
        requireNonNull(content, "content is null");
    }

    public <T> Optional<T> map(ObjectMapper mapper, Class<T> contentClass)
    {
        return content.map(c -> mapper.convertValue(c, contentClass));
    }
}

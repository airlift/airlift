package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Optional;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Icon(String src, Optional<String> mimeType, Optional<List<String>> sizes, Optional<Theme> theme)
{
    public Icon
    {
        requireNonNull(src, "src is null");
        mimeType = requireNonNullElse(mimeType, Optional.empty());
        sizes = requireNonNullElse(sizes, Optional.empty());
        theme = requireNonNullElse(theme, Optional.empty());
    }

    public Icon(String src)
    {
        this(src, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public enum Theme
    {
        UNDEFINED,
        LIGHT,
        DARK;

        @JsonValue
        public String toJsonValue()
        {
            if (this == UNDEFINED) {
                return null;
            }
            return name().toLowerCase(ROOT);
        }
    }
}

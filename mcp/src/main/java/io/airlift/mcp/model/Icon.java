package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record Icon(String src, Optional<String> mimeType, Optional<List<String>> sizes, Optional<Theme> theme)
{
    public Icon
    {
        requireNonNull(src, "src is null");
        mimeType = firstNonNull(mimeType, Optional.empty());
        sizes = firstNonNull(sizes, Optional.empty());
        theme = firstNonNull(theme, Optional.empty());
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

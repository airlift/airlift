package io.airlift.api;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public enum ApiEnumNamingFormat
{
    PASCAL_CASE("PascalCase", "^[A-Z][a-zA-Z0-9]*$"),
    UPPER_SNAKE_CASE("UPPER_SNAKE_CASE", "^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");

    private final String value;
    private final Pattern pattern;

    ApiEnumNamingFormat(String value, String pattern)
    {
        this.value = requireNonNull(value, "value is null");
        this.pattern = Pattern.compile(pattern);
    }

    public static ApiEnumNamingFormat fromString(String value)
    {
        requireNonNull(value, "value is null");
        for (ApiEnumNamingFormat format : values()) {
            if (format.value.equals(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown enum naming format: " + value);
    }

    public boolean isValid(String value)
    {
        return pattern.matcher(requireNonNull(value, "value is null")).matches();
    }

    @Override
    public String toString()
    {
        return value;
    }
}

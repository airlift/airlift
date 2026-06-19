package io.airlift.stats;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

public enum StatsBackend
{
    AIRLIFT,
    OPENTELEMETRY;

    static StatsBackend fromPropertyValue(String value)
    {
        String normalized = requireNonNull(value, "value is null")
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AIRLIFT" -> AIRLIFT;
            case "OPENTELEMETRY", "OTEL" -> OPENTELEMETRY;
            default -> throw new IllegalArgumentException("Unknown stats backend: " + value);
        };
    }
}

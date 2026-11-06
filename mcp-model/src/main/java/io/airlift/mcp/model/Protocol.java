package io.airlift.mcp.model;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

public enum Protocol
{
    PROTOCOL_MCP_2025_06_18("2025-06-18", 0),
    PROTOCOL_MCP_2025_11_25("2025-11-25", 1),
    PROTOCOL_MCP_2026_07_28("2026-07-28", 2);

    public static final Protocol LATEST_PROTOCOL = PROTOCOL_MCP_2026_07_28;

    public static final Protocol LAST_LEGACY_PROTOCOL = PROTOCOL_MCP_2025_11_25;

    private static final Map<String, Protocol> map = Stream.of(values())
            .collect(toImmutableMap(Protocol::value, Function.identity()));

    private final String value;
    private final int level;

    public static Optional<Protocol> of(String value)
    {
        return Optional.ofNullable(map.get(value));
    }

    public static Protocol min(Protocol first, Protocol second)
    {
        return first.isAtMost(second) ? first : second;
    }

    public String value()
    {
        return value;
    }

    @VisibleForTesting
    int level()
    {
        return level;
    }

    public boolean isAtMost(Protocol other)
    {
        return level <= other.level;
    }

    Protocol(String value, int level)
    {
        this.value = requireNonNull(value, "value is null");
        this.level = level;
    }
}

package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

public enum Protocol
{
    PROTOCOL_MCP_2025_06_18("2025-06-18", false, false),
    PROTOCOL_MCP_2025_11_25("2025-11-25", true, true);

    public static final Protocol LATEST_PROTOCOL = PROTOCOL_MCP_2025_11_25;

    private static final Map<String, Protocol> map = Stream.of(values())
            .collect(toImmutableMap(Protocol::value, Function.identity()));

    private final String value;
    private final boolean supportsIcons;
    private final boolean supportsTasks;

    public static Optional<Protocol> of(String value)
    {
        return Optional.ofNullable(map.get(value));
    }

    public String value()
    {
        return value;
    }

    public boolean supportsIcons()
    {
        return supportsIcons;
    }

    public boolean supportsTasks()
    {
        return supportsTasks;
    }

    Protocol(String value, boolean supportsIcons, boolean supportsTasks)
    {
        this.value = requireNonNull(value, "value is null");
        this.supportsIcons = supportsIcons;
        this.supportsTasks = supportsTasks;
    }
}

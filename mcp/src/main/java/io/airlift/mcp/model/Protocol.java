package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

public enum Protocol
{
    PROTOCOL_MCP_2025_06_18("2025-06-18"),
    PROTOCOL_MCP_2025_11_25("2025-11-25");

    public static final Protocol LATEST_PROTOCOL = PROTOCOL_MCP_2025_11_25;

    private static final Map<String, Protocol> map = Stream.of(values())
            .collect(toImmutableMap(Protocol::value, Function.identity()));

    private final String value;

    public static Optional<Protocol> of(String value)
    {
        return Optional.ofNullable(map.get(value));
    }

    public String value()
    {
        return value;
    }

    Protocol(String value)
    {
        this.value = requireNonNull(value, "value is null");
    }
}

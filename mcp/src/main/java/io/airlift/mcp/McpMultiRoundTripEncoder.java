package io.airlift.mcp;

public interface McpMultiRoundTripEncoder
{
    <T> String encode(Class<T> type, T object);

    <T> T decode(Class<T> type, String encoded);
}

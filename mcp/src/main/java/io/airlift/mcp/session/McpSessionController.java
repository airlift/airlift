package io.airlift.mcp.session;

import io.airlift.mcp.model.Event;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface McpSessionController
{
    String newSession();

    Set<String> currentSessionIds();

    boolean deleteSession(String sessionId);

    boolean isValidSession(String sessionId);

    boolean addEvent(String sessionId, String eventData);

    List<Event> pollEvents(String sessionId, long lastEventId, Duration timeout);

    <T> boolean upsertValue(String sessionId, McpValueKey<T> key, T value);

    <T> boolean deleteValue(String sessionId, McpValueKey<T> key);

    <T> Optional<T> currentValue(String sessionId, McpValueKey<T> key);
}

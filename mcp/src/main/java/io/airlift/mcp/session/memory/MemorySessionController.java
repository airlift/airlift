package io.airlift.mcp.session.memory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.McpValueKey;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class MemorySessionController
        implements McpSessionController
{
    private final Cache<String, MemorySession> sessions;

    @Inject
    public MemorySessionController(MemorySessionConfig config)
    {
        this(CacheBuilder.newBuilder()
                .expireAfterAccess(config.getSessionTimeout().toJavaTime())
                .build());
    }

    @VisibleForTesting
    public MemorySessionController(Cache<String, MemorySession> sessions)
    {
        this.sessions = requireNonNull(sessions, "sessions is null");
    }

    @Override
    public Set<String> currentSessionIds()
    {
        return ImmutableSet.copyOf(sessions.asMap().keySet());
    }

    @Override
    public <T> boolean upsertValue(String sessionId, McpValueKey<T> key, T value)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> {
                    session.setValue(key.name(), value);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> boolean deleteValue(String sessionId, McpValueKey<T> key)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> {
                    session.deleteValue(key.name());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> T currentValue(String sessionId, McpValueKey<T> key, T defaultValue)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .flatMap(session -> session.value(key.name()))
                .map(key.type()::cast)
                .orElse(defaultValue);
    }

    @Override
    public <T> Optional<T> waitValueCondition(String sessionId, Supplier<Optional<T>> conditionSupplier, Duration timeout)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .flatMap(session -> session.waitValueCondition(conditionSupplier, timeout));
    }

    @Override
    public Collection<String> currentValueKeys(String sessionId)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(MemorySession::currentKeys)
                .orElseGet(ImmutableSet::of);
    }

    @Override
    public String newSession()
    {
        MemorySession session = new MemorySession();
        String sessionId = newSessionId();
        sessions.put(sessionId, session);

        return sessionId;
    }

    @Override
    public boolean deleteSession(String sessionId)
    {
        return sessions.asMap().remove(sessionId) != null;
    }

    @Override
    public boolean isValidSession(String sessionId)
    {
        return sessions.asMap().containsKey(sessionId);
    }

    private static String newSessionId()
    {
        // per the spec: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
        // - The session ID SHOULD be globally unique and cryptographically secure (e.g., a securely generated UUID, a JWT, or a cryptographic hash).
        // - The session ID MUST only contain visible ASCII characters (ranging from 0x21 to 0x7E).
        return UUID.randomUUID().toString().replace("-", "");
    }
}

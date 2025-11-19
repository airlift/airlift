package io.airlift.mcp.sessions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class MemorySessionController
        implements SessionController
{
    public static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(15);

    private final Cache<SessionId, Session> sessions;

    public record Session(Map<SessionKey<?>, Object> values)
    {
        public Session
        {
            requireNonNull(values, "values is null");
        }
    }

    @Inject
    public MemorySessionController()
    {
        this(DEFAULT_SESSION_TIMEOUT);
    }

    public MemorySessionController(Duration sessionTimeout)
    {
        this(CacheBuilder.newBuilder()
                .expireAfterAccess(sessionTimeout)
                .build());
    }

    @VisibleForTesting
    public MemorySessionController(Cache<SessionId, Session> sessions)
    {
        this.sessions = requireNonNull(sessions, "sessions is null");
    }

    @VisibleForTesting
    public Set<SessionId> sessionIds()
    {
        return ImmutableSet.copyOf(sessions.asMap().keySet());
    }

    @Override
    public SessionId createSession(HttpServletRequest request)
    {
        SessionId sessionId = new SessionId(UUID.randomUUID().toString());
        sessions.put(sessionId, new Session(new ConcurrentHashMap<>()));
        return sessionId;
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        return sessions.asMap().containsKey(sessionId);
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        sessions.invalidate(sessionId);
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionKey<T> key)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> session.values().get(key))
                .map(key.type()::cast);
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionKey<T> key, T value)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> {
                    session.values().put(key, value);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> boolean computeSessionValue(SessionId sessionId, SessionKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> {
                    session.values().compute(key, (_, existingValue) -> {
                        Optional<T> existing = Optional.ofNullable(existingValue).map(key.type()::cast);
                        return updater.apply(existing).orElse(null);
                    });
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionKey<T> key)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(session -> {
                    session.values().remove(key);
                    return true;
                })
                .orElse(false);
    }
}

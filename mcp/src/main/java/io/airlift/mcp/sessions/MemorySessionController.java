package io.airlift.mcp.sessions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class MemorySessionController
        implements SessionController
{
    public static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(15);

    private final Cache<SessionId, Session> sessions;

    public static class Session
    {
        private final Map<Class<?>, Map<String, Object>> values = new ConcurrentHashMap<>();

        private Map<String, Object> typeMap(Class<?> type)
        {
            return values.computeIfAbsent(type, _ -> new ConcurrentHashMap<>());
        }

        private <T> Optional<T> get(SessionValueKey<T> key)
        {
            return Optional.ofNullable(typeMap(key.type()).get(key.name()))
                    .map(key.type()::cast);
        }

        private <T> void put(SessionValueKey<T> key, T value)
        {
            typeMap(key.type()).put(key.name(), value);
        }

        private <T> void compute(SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
        {
            typeMap(key.type()).compute(key.name(), (_, existingValue) -> {
                Optional<T> existing = Optional.ofNullable(existingValue).map(key.type()::cast);
                return updater.apply(existing).orElse(null);
            });
        }

        private void remove(SessionValueKey<?> key)
        {
            typeMap(key.type()).remove(key.name());
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
        sessions.put(sessionId, new Session());
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
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        return getSession(sessionId)
                .flatMap(session -> session.get(key));
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        return getSession(sessionId)
                .map(session -> {
                    session.put(key, value);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> boolean computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return getSession(sessionId)
                .map(session -> {
                    session.compute(key, updater);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        return getSession(sessionId)
                .map(session -> {
                    session.remove(key);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> cursor)
    {
        return getSession(sessionId).map(session -> session.typeMap(type).entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> cursor.isEmpty() || cursor.get().compareTo(entry.getKey()) < 0)
                .limit(pageSize)
                .map(entry -> Map.entry(entry.getKey(), type.cast(entry.getValue())))
                .collect(toImmutableList()))
                .orElseGet(ImmutableList::of);
    }

    @Override
    public List<SessionId> listSessions(int pageSize, Optional<SessionId> cursor)
    {
        return sessions.asMap().keySet().stream()
                .sorted(Comparator.comparing(SessionId::id))
                .filter(entry -> cursor.isEmpty() || cursor.get().id().compareTo(entry.id()) < 0)
                .limit(pageSize)
                .collect(toImmutableList());
    }

    private Optional<Session> getSession(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId));
    }
}

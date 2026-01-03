package io.airlift.mcp.sessions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.model.McpIdentity;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.sessions.SessionConditionUtil.waitForCondition;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MemorySessionController
        implements SessionController
{
    private static final Logger log = Logger.get(MemorySessionController.class);

    private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Duration cleanupInterval;
    private final ConcurrentHashMap<SessionId, Session> sessions;
    private final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.now());

    public static class Session
    {
        private final Map<Class<?>, Map<String, Object>> values = new ConcurrentHashMap<>();
        private final Optional<Duration> ttl;
        private final Signal signal = new Signal();
        private volatile Instant lastUsage = Instant.now();

        private Session(Optional<Duration> ttl)
        {
            this.ttl = requireNonNull(ttl, "ttl is null");
        }

        private Map<String, Object> typeMap(Class<?> type)
        {
            return values.computeIfAbsent(type, _ -> new ConcurrentHashMap<>());
        }

        private <T> Optional<T> get(SessionValueKey<T> key)
        {
            lastUsage = Instant.now();

            return Optional.ofNullable(typeMap(key.type()).get(key.name()))
                    .map(key.type()::cast);
        }

        private <T> void put(SessionValueKey<T> key, T value)
        {
            lastUsage = Instant.now();

            typeMap(key.type()).put(key.name(), value);

            signal.signalAll();
        }

        private <T> Optional<T> compute(SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
        {
            lastUsage = Instant.now();

            Object computed = typeMap(key.type()).compute(key.name(), (_, existingValue) -> {
                Optional<T> existing = Optional.ofNullable(existingValue).map(key.type()::cast);
                return updater.apply(existing).orElse(null);
            });

            signal.signalAll();

            return Optional.ofNullable(computed).map(key.type()::cast);
        }

        private void remove(SessionValueKey<?> key)
        {
            lastUsage = Instant.now();

            typeMap(key.type()).remove(key.name());

            signal.signalAll();
        }

        private boolean canBeExpired(Instant now)
        {
            return ttl.map(localTtl -> lastUsage.plus(localTtl).isBefore(now))
                    .orElse(false);
        }
    }

    @Inject
    public MemorySessionController()
    {
        this(DEFAULT_CLEANUP_INTERVAL);
    }

    @VisibleForTesting
    public MemorySessionController(Duration cleanupInterval)
    {
        this.cleanupInterval = cleanupInterval;
        sessions = new ConcurrentHashMap<>();
    }

    @VisibleForTesting
    public Set<SessionId> sessionIds()
    {
        return ImmutableSet.copyOf(sessions.keySet());
    }

    @Override
    public SessionId createSession(Optional<McpIdentity> identity, Optional<Duration> ttl)
    {
        clean();

        SessionId sessionId = new SessionId(UUID.randomUUID().toString());
        sessions.put(sessionId, new Session(ttl));
        return sessionId;
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        clean();

        return sessions.containsKey(sessionId);
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        clean();

        sessions.remove(sessionId);
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        clean();

        return getSession(sessionId)
                .flatMap(session -> session.get(key));
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        clean();

        return getSession(sessionId)
                .map(session -> {
                    session.put(key, value);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> Optional<T> computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        clean();

        return getSession(sessionId)
                .flatMap(session -> session.compute(key, updater));
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        clean();

        return getSession(sessionId)
                .map(session -> {
                    session.remove(key);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public <T> void blockUntilCondition(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Function<Optional<T>, Boolean> condition)
            throws InterruptedException
    {
        waitForCondition(this, sessionId, key, timeout, condition, maxWait -> {
            Optional<Session> maybeSession = getSession(sessionId);
            if (maybeSession.isEmpty()) {
                MILLISECONDS.sleep(maxWait.toMillis());
                return;
            }

            Session session = maybeSession.get();
            session.signal.waitForSignal(maxWait.toMillis(), MILLISECONDS);
        });
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
        return sessions.keySet().stream()
                .sorted(Comparator.comparing(SessionId::id))
                .filter(entry -> cursor.isEmpty() || cursor.get().id().compareTo(entry.id()) < 0)
                .limit(pageSize)
                .collect(toImmutableList());
    }

    private Optional<Session> getSession(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    private void clean()
    {
        Instant now = Instant.now();
        Instant localLastCleanup = lastCleanup.get();
        Duration lastCleanupAge = Duration.between(localLastCleanup, now);

        if (lastCleanupAge.compareTo(cleanupInterval) >= 0) {
            if (lastCleanup.compareAndSet(localLastCleanup, now)) {
                for (Map.Entry<SessionId, Session> entry : sessions.entrySet()) {
                    if (entry.getValue().canBeExpired(now)) {
                        log.info("Cleaning up stale session %s", entry.getKey());
                        sessions.remove(entry.getKey());
                    }
                }
            }
        }
    }
}

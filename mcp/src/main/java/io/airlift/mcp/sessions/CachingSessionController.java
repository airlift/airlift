package io.airlift.mcp.sessions;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.RESOURCE_VERSIONS;
import static io.airlift.mcp.sessions.SessionValueKey.SYSTEM_LIST_VERSIONS;
import static java.util.Objects.requireNonNull;

public class CachingSessionController
        implements SessionController
{
    private static final Set<Class<?>> CACHEABLE_TYPES = Set.of(
            CLIENT_CAPABILITIES.type(),
            PROTOCOL.type(),
            SYSTEM_LIST_VERSIONS.type(),
            RESOURCE_VERSIONS.type());

    private final SessionController delegate;
    private final Cache<CacheKey<?>, Optional<?>> cache;

    private record CacheKey<T>(SessionId sessionId, SessionValueKey<T> key)
    {
        private CacheKey
        {
            requireNonNull(sessionId, "sessionId is null");
            requireNonNull(key, "key is null");
        }
    }

    @Inject
    public CachingSessionController(@ForSessionCaching SessionController delegate, McpConfig mcpConfig)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");

        cache = CacheBuilder.newBuilder()
                .expireAfterAccess(mcpConfig.getDefaultSessionTimeout().toJavaTime())
                .maximumSize(mcpConfig.getMaxSessionCache())
                .build();
    }

    @Override
    public SessionId createSession(McpIdentity identity, Optional<Duration> ttl)
    {
        return delegate.createSession(identity, ttl);
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        return delegate.validateSession(sessionId);
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        delegate.deleteSession(sessionId);
    }

    @Override
    public <T> BlockingResult<T> blockUntil(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        return delegate.blockUntil(sessionId, key, timeout, condition);
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        if (isCacheable(key)) {
            try {
                return cache.get(cacheKey(sessionId, key), () -> delegate.getSessionValue(sessionId, key))
                        .map(key.type()::cast);
            }
            catch (ExecutionException e) {
                throw new UncheckedExecutionException(e);
            }
        }

        return delegate.getSessionValue(sessionId, key);
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        if (isCacheable(key)) {
            // it is the responsibility of the caller to keep cached values consistent
            cache.put(cacheKey(sessionId, key), Optional.of(value));
        }

        return delegate.setSessionValue(sessionId, key, value);
    }

    @Override
    public <T> Optional<T> computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        if (isCacheable(key)) {
            return delegate.computeSessionValue(sessionId, key, currentValue -> {
                Optional<T> newValue = updater.apply(currentValue);
                // it is the responsibility of the caller to keep cached values consistent
                cache.put(cacheKey(sessionId, key), newValue);
                return newValue;
            });
        }

        return delegate.computeSessionValue(sessionId, key, updater);
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        if (isCacheable(key)) {
            // it is the responsibility of the caller to keep cached values consistent
            cache.put(cacheKey(sessionId, key), Optional.empty());
        }

        return delegate.deleteSessionValue(sessionId, key);
    }

    @Override
    public <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> cursor)
    {
        return delegate.listSessionValues(sessionId, type, pageSize, cursor);
    }

    @Override
    public List<SessionId> listSessions(int pageSize, Optional<SessionId> cursor)
    {
        return delegate.listSessions(pageSize, cursor);
    }

    private static <T> boolean isCacheable(SessionValueKey<T> key)
    {
        return CACHEABLE_TYPES.contains(key.type());
    }

    private static <T> CacheKey<T> cacheKey(SessionId sessionId, SessionValueKey<T> key)
    {
        return new CacheKey<>(sessionId, key);
    }
}

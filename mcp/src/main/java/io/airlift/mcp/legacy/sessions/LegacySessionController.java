package io.airlift.mcp.legacy.sessions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static java.util.Objects.requireNonNull;

public class LegacySessionController
{
    private final Cache<LegacySessionId, Entry> sessions;

    private record Entry(Map<LegacySessionValueKey<?>, Object> values, Signal signal)
    {
        private Entry
        {
            requireNonNull(values, "values is null");   // don't copy
            requireNonNull(signal, "signal is null");
        }
    }

    public static LegacySessionId requireSessionId(HttpServletRequest request)
    {
        return optionalSessionId(request).orElseThrow(() -> exception("Missing %s header in request".formatted(MCP_SESSION_ID)));
    }

    public static Optional<LegacySessionId> optionalSessionId(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .map(LegacySessionId::new);
    }

    @Inject
    public LegacySessionController(McpConfig config)
    {
        sessions = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getDefaultSessionTimeout().toJavaTime())
                .build();
    }

    public boolean isValidSession(LegacySessionId sessionId)
    {
        return sessions.asMap().containsKey(sessionId);
    }

    public LegacySession createSession()
    {
        LegacySessionId sessionId = new LegacySessionId(UUID.randomUUID().toString());

        Entry entry = new Entry(new ConcurrentHashMap<>(), new Signal());
        sessions.put(sessionId, entry);

        return new LegacySessionImpl(sessionId, entry.values, entry.signal, () -> isValidSession(sessionId));
    }

    public Optional<LegacySession> session(LegacySessionId sessionId)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(entry -> new LegacySessionImpl(sessionId, entry.values, entry.signal, () -> isValidSession(sessionId)));
    }

    public void deleteSession(LegacySessionId sessionId)
    {
        sessions.invalidate(sessionId);
    }

    @VisibleForTesting
    public Set<LegacySessionId> sessionIds()
    {
        return ImmutableSet.copyOf(sessions.asMap().keySet());
    }

    @VisibleForTesting
    public Map<LegacySessionValueKey<?>, Object> sessionValues(LegacySessionId sessionId)
    {
        return Optional.ofNullable(sessions.getIfPresent(sessionId))
                .map(Entry::values)
                .orElseGet(ImmutableMap::of);
    }
}

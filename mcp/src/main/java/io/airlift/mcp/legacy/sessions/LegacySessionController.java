package io.airlift.mcp.legacy.sessions;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.storage.Storage;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static java.util.Objects.requireNonNull;

public class LegacySessionController
{
    private final Storage storage;
    private final JsonMapper jsonMapper;
    private final Duration sessionTimeout;

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
    public LegacySessionController(McpConfig config, Storage storage, JsonMapper jsonMapper)
    {
        this.storage = requireNonNull(storage, "storage is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");

        sessionTimeout = config.getDefaultSessionTimeout().toJavaTime();
    }

    public boolean isValidSession(LegacySessionId sessionId)
    {
        return storage.groupExists(sessionId.id());
    }

    public LegacySession createSession()
    {
        LegacySessionId sessionId = new LegacySessionId(UUID.randomUUID().toString());
        storage.createGroup(sessionId.id(), sessionTimeout);

        return new LegacySessionImpl(sessionId, storage, jsonMapper);
    }

    public Optional<LegacySession> session(LegacySessionId sessionId)
    {
        return storage.groupExists(sessionId.id())
                ? Optional.of(new LegacySessionImpl(sessionId, storage, jsonMapper))
                : Optional.empty();
    }

    public void deleteSession(LegacySessionId sessionId)
    {
        storage.deleteGroup(sessionId.id());
    }

    @VisibleForTesting
    public Set<LegacySessionId> sessionIds()
    {
        return storage.groups()
                .map(LegacySessionId::new)
                .collect(toImmutableSet());
    }
}

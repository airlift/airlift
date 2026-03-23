package io.airlift.mcp.operations;

import io.airlift.mcp.McpException;
import io.airlift.mcp.sessions.BlockingResult;
import io.airlift.mcp.sessions.Session;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

class SessionImpl
        implements Session
{
    private static final Supplier<McpException> ERROR_SESSIONS_ARE_NOT_ENABLED = () -> exception(INVALID_REQUEST, "Sessions are not enabled");

    private final Optional<SessionController> sessionController;
    private final SessionId sessionId;

    SessionImpl(Optional<SessionController> sessionController, SessionId sessionId)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.sessionId = requireNonNull(sessionId, "sessionId is null");
    }

    @Override
    public SessionId sessionId()
    {
        return sessionId;
    }

    @Override
    public boolean isValid()
    {
        if (sessionId.equals(NULL_SESSION_ID)) {
            return false;
        }

        return sessionController.map(localSessionController -> localSessionController.validateSession(sessionId))
                .orElse(false);
    }

    @Override
    public <T> BlockingResult<T> blockUntil(SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        return sessionController.orElseThrow(ERROR_SESSIONS_ARE_NOT_ENABLED)
                .blockUntil(sessionId, key, timeout, condition);
    }

    @Override
    public <T> Optional<T> getValue(SessionValueKey<T> key)
    {
        return sessionController.orElseThrow(ERROR_SESSIONS_ARE_NOT_ENABLED)
                .getSessionValue(sessionId, key);
    }

    @Override
    public <T> boolean setValue(SessionValueKey<T> key, T value)
    {
        return sessionController.orElseThrow(ERROR_SESSIONS_ARE_NOT_ENABLED)
                .setSessionValue(sessionId, key, value);
    }

    @Override
    public <T> Optional<T> computeValue(SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return sessionController.orElseThrow(ERROR_SESSIONS_ARE_NOT_ENABLED)
                .computeSessionValue(sessionId, key, updater);
    }

    @Override
    public <T> boolean deleteValue(SessionValueKey<T> key)
    {
        return sessionController.orElseThrow(ERROR_SESSIONS_ARE_NOT_ENABLED)
                .deleteSessionValue(sessionId, key);
    }
}

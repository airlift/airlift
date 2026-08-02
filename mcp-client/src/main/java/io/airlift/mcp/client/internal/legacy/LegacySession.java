package io.airlift.mcp.client.internal.legacy;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.mcp.model.InitializeResult;

import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The session a legacy connection, and every connection derived from it, sends its requests in. A server may end a
 * session at any time, after which a new one is started in its place.
 */
final class LegacySession
{
    record State(InitializeResult initializeResult, Optional<String> sessionId)
    {
        State
        {
            requireNonNull(initializeResult, "initializeResult is null");
            requireNonNull(sessionId, "sessionId is null");
        }
    }

    private final Supplier<State> initializer;

    @GuardedBy("this")
    private State state;

    LegacySession(Supplier<State> initializer)
    {
        this.initializer = requireNonNull(initializer, "initializer is null");
        this.state = initializer.get();
    }

    synchronized State state()
    {
        return state;
    }

    /**
     * Starts a new session in place of {@code endedSessionId} and returns the session to use from now on. Requests
     * that fail at the same time all name the same ended session, so only the first of them starts a new one - the
     * rest find it already replaced and use the session it started.
     */
    synchronized State restart(Optional<String> endedSessionId)
    {
        if (state.sessionId().equals(endedSessionId)) {
            state = initializer.get();
        }
        return state;
    }
}

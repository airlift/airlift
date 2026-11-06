package io.airlift.mcp.client.internal.legacy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpMapper;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.airlift.http.client.HttpStatus.FORBIDDEN;
import static io.airlift.http.client.HttpStatus.REQUEST_TIMEOUT;
import static io.airlift.http.client.HttpStatus.TOO_MANY_REQUESTS;
import static io.airlift.http.client.HttpStatus.UNAUTHORIZED;
import static io.airlift.http.client.HttpStatus.familyForStatusCode;
import static io.airlift.mcp.client.internal.ErrorResponseException.responseStatusCode;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.CURRENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LATENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LEGACY;
import static io.airlift.mcp.model.JsonRpcErrorCode.HEADER_MISMATCH;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static java.util.Objects.requireNonNull;

// all LegacyOptionalConnection instances share the same LegacyOptionalSharedState instance, which is used to manage the transition between legacy and current connections
// this is because it represents a single server connection, and the transition between legacy and current connections is a global state for that server connection
final class LegacyOptionalSharedState
        implements Closeable
{
    // client errors about the request's credentials or rate rather than its protocol
    private static final Set<Integer> NON_PROTOCOL_CLIENT_ERRORS = ImmutableSet.of(UNAUTHORIZED.code(), FORBIDDEN.code(), REQUEST_TIMEOUT.code(), TOO_MANY_REQUESTS.code());

    private final AtomicReference<State> state;
    private final Supplier<LegacyConnection> legacyConnectionSupplier;
    private final Closer closer;
    private final Lock transitionLock;

    LegacyOptionalSharedState(Supplier<LegacyConnection> givenLegacyConnectionSupplier)
    {
        requireNonNull(givenLegacyConnectionSupplier, "givenLegacyConnectionSupplier is null");

        this.state = new AtomicReference<>(State.LATENT);
        this.closer = Closer.create();
        this.legacyConnectionSupplier = Suppliers.memoize(() -> closer.register(givenLegacyConnectionSupplier.get()));
        this.transitionLock = new ReentrantLock();
    }

    enum State
    {
        LATENT,
        LEGACY,
        CURRENT,
    }

    @Override
    public void close()
    {
        try {
            closer.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    State state()
    {
        return state.get();
    }

    LegacyConnection legacyConnection()
    {
        return legacyConnectionSupplier.get();
    }

    <R> R withConnectionResolution(Supplier<R> proc)
    {
        if (state.get() != LATENT) {
            return proc.get();
        }

        // the request is sent without holding the lock, so that a slow first request does not hold up the others.
        // While the protocol is undecided, concurrent requests each go out on the current protocol, and the lock only
        // guards recording what they show - the first of them to settle the protocol decides it
        try {
            R result = proc.get();
            resolve(CURRENT);
            return result;
        }
        catch (McpException mcpException) {
            // nothing in the failure says which protocol the server speaks - stay undecided so that the next request probes again
            State resolvedState = resolveProtocol(mcpException).orElseThrow(() -> mcpException);

            if ((resolve(resolvedState) != LEGACY) || (resolvedState != LEGACY)) {
                // the current protocol is fine - this failure was about something else
                throw mcpException;
            }

            // the server rejected the request without acting on it, so it is sent again on the legacy connection
            return proc.get();
        }
    }

    private State resolve(State resolvedState)
    {
        transitionLock.lock();
        try {
            transition(resolvedState);
            return state.get();
        }
        finally {
            transitionLock.unlock();
        }
    }

    @GuardedBy("transitionLock")
    private void transition(State newState)
    {
        if (state.get() == State.LATENT) {
            switch (newState) {
                case LATENT -> throw new IllegalArgumentException("Cannot transition to LATENT state");
                case LEGACY -> {
                    // materialize before committing - the state should only move if there is actually a legacy connection
                    legacyConnectionSupplier.get();
                    state.set(newState);
                }
                case CURRENT -> state.set(newState);
            }
        }
    }

    /**
     * The protocol a failed first request shows the server to speak, or empty if the failure does not say. Only what
     * the server answered can say - a failure on this side of the connection, such as a response that could not be
     * read, must not cause the request to be sent again as the server may already have acted on it.
     */
    @VisibleForTesting
    static Optional<State> resolveProtocol(McpException mcpException)
    {
        int code = mcpException.errorDetail().code();
        if (code == UNSUPPORTED_PROTOCOL.code()) {
            boolean offersLegacyProtocol = parseProtocolError(mcpException)
                    .map(error -> error.supported().contains(PROTOCOL_MCP_2025_11_25.value()))
                    .orElse(true);  // no UnsupportedProtocolVersionError present - assume it can support the legacy protocol
            return Optional.of(offersLegacyProtocol ? LEGACY : CURRENT);
        }

        if ((code == HEADER_MISMATCH.code()) || (code == MISSING_REQUIRED_CLIENT_CAPABILITY.code())) {
            // errors only a server speaking the current protocol sends
            return Optional.of(CURRENT);
        }

        return responseStatusCode(mcpException).flatMap(statusCode -> switch (familyForStatusCode(statusCode)) {
            // the server answered the request with a JSON-RPC error, so it accepted the current protocol
            case SUCCESSFUL -> Optional.of(CURRENT);

            // https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http#backward-compatibility
            // the server rejected the request without acting on it, so it is safe to send it again
            case CLIENT_ERROR -> NON_PROTOCOL_CLIENT_ERRORS.contains(statusCode) ? Optional.empty() : Optional.of(LEGACY);

            // server errors, and anything else, say nothing about the protocol
            default -> Optional.empty();
        });
    }

    private static Optional<UnsupportedProtocolVersionError> parseProtocolError(McpException mcpException)
    {
        return mcpException.errorDetail().data()
                .flatMap(data -> {
                    try {
                        return Optional.of(McpMapper.jsonMapper().convertValue(data, UnsupportedProtocolVersionError.class));
                    }
                    catch (IllegalArgumentException _) {
                        // ignore
                    }
                    return Optional.empty();
                });
    }
}

package io.airlift.mcp.client.internal.legacy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.io.Closer;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpMapper;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.CURRENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LATENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LEGACY;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static java.util.Objects.requireNonNull;

// all LegacyOptionalConnection instances share the same LegacyOptionalSharedState instance, which is used to manage the transition between legacy and current connections
// this is because it represents a single server connection, and the transition between legacy and current connections is a global state for that server connection
final class LegacyOptionalSharedState
        implements Closeable
{
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
        if (state.get() == LATENT) {
            transitionLock.lock();
            try {
                // essentially a double-checked lock to avoid unnecessary locking in the common case
                if (state.get() == LATENT) {
                    try {
                        R result = proc.get();
                        transition(CURRENT);
                        return result;
                    }
                    catch (McpException mcpException) {
                        if (indicatesLegacyProtocol(mcpException)) {
                            transition(LEGACY);
                            // will retry with the legacy connection below
                        }
                        else {
                            // the current protocol is fine - this failure was about something else
                            transition(CURRENT);
                            throw mcpException;
                        }
                    }
                }
            }
            finally {
                transitionLock.unlock();
            }
        }

        return proc.get();
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

    @VisibleForTesting
    static boolean indicatesLegacyProtocol(McpException mcpException)
    {
        if (mcpException.errorDetail().code() == UNSUPPORTED_PROTOCOL.code()) {
            return parseProtocolError(mcpException)
                    .map(error -> error.supported().contains(PROTOCOL_MCP_2025_11_25.value()))
                    .orElse(true);  // no UnsupportedProtocolVersionError present - assume it can support the legacy protocol
        }

        if ((Throwables.getRootCause(mcpException) instanceof UnexpectedResponseException responseException) && (responseException.getStatusCode() == BAD_REQUEST.code())) {
            return true;
        }

        return mcpException.errorDetail().code() == INVALID_REQUEST.code();
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

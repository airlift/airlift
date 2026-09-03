package io.airlift.mcp.client.internal.legacy;

import com.google.common.base.Suppliers;
import com.google.common.io.Closer;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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

    void setState(State newState)
    {
        if (newState == State.LEGACY) {
            // Create the legacy connection early so that it initializes
            legacyConnectionSupplier.get();
        }
        state.set(newState);
    }

    LegacyConnection legacyConnection()
    {
        return legacyConnectionSupplier.get();
    }

    Lock transitionLock()
    {
        return transitionLock;
    }
}

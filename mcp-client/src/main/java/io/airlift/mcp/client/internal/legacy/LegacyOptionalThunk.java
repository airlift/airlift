package io.airlift.mcp.client.internal.legacy;

import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.InternalConnection;
import io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

class LegacyOptionalThunk
        implements Closeable
{
    private final InternalConnection internalConnection;
    private final AtomicReference<LegacyConnection> legacyConnection = new AtomicReference<>();

    LegacyOptionalThunk(InternalConnection internalConnection)
    {
        this.internalConnection = requireNonNull(internalConnection, "internalConnection is null");
    }

    InternalConnection internalConnection()
    {
        return internalConnection;
    }

    McpTasksConnection get(LegacyOptionalSharedState sharedState)
    {
        return switch (sharedState.state()) {
            case LEGACY -> legacyConnection(sharedState);
            case LATENT, CURRENT -> internalConnection;
        };
    }

    <V> LegacyOptionalThunk withSetting(McpConnectionSetting<V> setting, V value)
    {
        // internalConnection is source of truth for settings
        // the new LegacyOptionalThunk's legacyConnection will be created lazily when needed and will use the settings from internalConnection
        return new LegacyOptionalThunk(internalConnection.withSetting(setting, value));
    }

    void transition(LegacyOptionalSharedState sharedState, State newState)
    {
        sharedState.transitionLock().lock();
        try {
            if (sharedState.state() == State.LATENT) {
                switch (newState) {
                    case LATENT -> throw new IllegalArgumentException("Cannot transition to LATENT state");
                    case LEGACY, CURRENT -> sharedState.setState(newState);
                }
            }
        }
        finally {
            sharedState.transitionLock().unlock();
        }
    }

    @Override
    public void close()
    {
        internalConnection.close();
    }

    private LegacyConnection legacyConnection(LegacyOptionalSharedState sharedState)
    {
        return legacyConnection.updateAndGet(current -> {
            if (current != null) {
                return current;
            }

            return sharedState.rewLegacyConnection().withMergedSettingContainer(internalConnection.settingContainer());
        });
    }
}

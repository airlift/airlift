package io.airlift.mcp.reference;

import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.McpValueKey;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

class ListVersion
{
    static final UUID NULL_UUID = new UUID(0L, 0L);

    private final McpSessionController sessionController;
    private final String sessionId;
    private final McpValueKey<UUID> key;
    private final String notification;

    private UUID currentVersion;

    ListVersion(McpSessionController sessionController, String sessionId, McpValueKey<UUID> key, String notification)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.sessionId = requireNonNull(sessionId, "sessionId is null");
        this.key = requireNonNull(key, "key is null");
        this.notification = notification;

        currentVersion = sessionController.currentValue(sessionId, key, NULL_UUID);
    }

    String notification()
    {
        return notification;
    }

    boolean wasUpdated()
    {
        if (currentVersion.equals(sessionController.currentValue(sessionId, key, NULL_UUID))) {
            return false;
        }
        currentVersion = sessionController.currentValue(sessionId, key, NULL_UUID);
        return true;
    }
}

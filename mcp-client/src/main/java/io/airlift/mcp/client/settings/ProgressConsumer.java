package io.airlift.mcp.client.settings;

import io.airlift.mcp.model.ProgressNotification;

import java.util.Optional;

import static io.airlift.mcp.client.McpMapper.optionalProgressNotification;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;

public interface ProgressConsumer
        extends NotificationConsumer
{
    void accept(ProgressNotification progressNotification);

    @Override
    default void accept(Object id, String method, Optional<Object> params)
    {
        if (method.equals(NOTIFICATION_PROGRESS)) {
            optionalProgressNotification(params).ifPresent(this::accept);
        }
    }
}

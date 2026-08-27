package io.airlift.mcp.client.settings;

import io.airlift.mcp.model.LoggingMessageNotification;

import java.util.Optional;

import static io.airlift.mcp.client.McpMapper.optionalLoggingMessageNotification;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;

public interface LoggingConsumer
        extends NotificationConsumer
{
    void accept(LoggingMessageNotification notification);

    @Override
    default void accept(Object id, String method, Optional<Object> params)
    {
        if (method.equals(NOTIFICATION_MESSAGE)) {
            optionalLoggingMessageNotification(params).ifPresent(this::accept);
        }
    }
}

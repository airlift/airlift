package io.airlift.mcp.client.settings;

import io.airlift.mcp.model.LoggingMessageNotification;

import static io.airlift.mcp.client.McpMapper.requireLoggingMessageNotification;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;

public interface LoggingConsumer
{
    void accept(LoggingMessageNotification notification);

    default NotificationConsumer asNotificationConsumer()
    {
        return (_, method, params) -> {
            if (method.equals(NOTIFICATION_MESSAGE)) {
                accept(requireLoggingMessageNotification(params));
            }
        };
    }
}

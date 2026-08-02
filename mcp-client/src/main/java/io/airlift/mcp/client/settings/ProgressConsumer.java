package io.airlift.mcp.client.settings;

import io.airlift.mcp.model.ProgressNotification;

import static io.airlift.mcp.client.McpMapper.requireProgressNotification;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;

public interface ProgressConsumer
{
    void accept(ProgressNotification progressNotification);

    default NotificationConsumer asNotificationConsumer()
    {
        return (_, method, params) -> {
            if (method.equals(NOTIFICATION_PROGRESS)) {
                accept(requireProgressNotification(params));
            }
        };
    }
}

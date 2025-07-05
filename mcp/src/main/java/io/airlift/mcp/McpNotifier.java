package io.airlift.mcp;

import java.util.Optional;

public interface McpNotifier
{
    void notifyProgress(String message, Optional<Double> progress, Optional<Double> total);

    <T> void sendNotification(String notificationType, T data);

    void sendNotification(String notificationType);
}

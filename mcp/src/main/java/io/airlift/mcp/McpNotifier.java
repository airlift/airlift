package io.airlift.mcp;

import java.util.Optional;

public interface McpNotifier
{
    void notifyProgress(String message, Optional<Double> progress, Optional<Double> total);

    void sendNotification(String notificationType, Object data);
}

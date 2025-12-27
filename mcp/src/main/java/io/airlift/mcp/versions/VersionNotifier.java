package io.airlift.mcp.versions;

import java.util.Optional;

@FunctionalInterface
public interface VersionNotifier
{
    void sendNotification(String method, Optional<Object> params);
}

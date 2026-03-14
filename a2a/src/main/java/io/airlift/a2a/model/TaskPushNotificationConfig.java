package io.airlift.a2a.model;

import java.util.Optional;

public record TaskPushNotificationConfig(Optional<String> tenant, Optional<String> id, Optional<String> taskId, String url, Optional<String> token, Optional<AuthenticationInfo> authentication)
        implements Tenant
{
}

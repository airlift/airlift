package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record SubscriptionNotifications(SubscriptionFilter notifications, Optional<Map<String, Object>> meta)
        implements Meta
{
    public SubscriptionNotifications
    {
        requireNonNull(notifications, "notifications is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public SubscriptionNotifications withMeta(Map<String, Object> meta)
    {
        return new SubscriptionNotifications(notifications, Optional.of(meta));
    }
}

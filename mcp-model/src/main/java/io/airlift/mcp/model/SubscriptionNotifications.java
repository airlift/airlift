package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record SubscriptionNotifications(SubscriptionFilter notifications, Optional<Map<String, Object>> meta)
        implements Meta<SubscriptionNotifications>
{
    public SubscriptionNotifications
    {
        requireNonNull(notifications, "notifications is null");
        meta = normalize(meta);
    }

    @Override
    public SubscriptionNotifications withMeta(Map<String, Object> meta)
    {
        return new SubscriptionNotifications(notifications, Optional.of(meta));
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record SubscriptionNotifications(SubscriptionFilter notifications, Optional<Map<String, Object>> meta)
        implements Meta<SubscriptionNotifications>
{
    public SubscriptionNotifications
    {
        requireNonNull(notifications, "notifications is null");
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public SubscriptionNotifications withMeta(Map<String, Object> meta)
    {
        return new SubscriptionNotifications(notifications, Optional.of(meta));
    }
}

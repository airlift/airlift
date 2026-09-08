package io.airlift.mcp.client.settings;

import java.util.Optional;

public interface NotificationConsumer
{
    void accept(Object id, String method, Optional<Object> params);

    default NotificationConsumer andThen(NotificationConsumer after)
    {
        return (id, method, params) -> {
            accept(id, method, params);
            after.accept(id, method, params);
        };
    }
}

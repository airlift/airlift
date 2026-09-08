package io.airlift.mcp.client.settings;

import io.airlift.http.client.Request;
import io.airlift.mcp.model.CacheableResult;

import java.util.Optional;
import java.util.function.Supplier;

public interface RequestCache
        extends NotificationConsumer
{
    <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier);

    @Override
    default void accept(Object id, String method, Optional<Object> params)
    {
        // default does nothing, can be overridden by implementations to perform cache invalidation on notifications
    }
}

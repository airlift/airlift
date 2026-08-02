package io.airlift.mcp.client.settings;

import io.airlift.http.client.Request;
import io.airlift.mcp.model.CacheableResult;

import java.util.Optional;
import java.util.function.Supplier;

public interface RequestCache
{
    <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier);
}

package io.airlift.mcp.reference;

import com.google.common.collect.Sets;
import io.airlift.mcp.model.JsonRpcResponse;

import java.util.Set;
import java.util.function.BiConsumer;

public class ResponseListenerController
{
    private final Set<BiConsumer<String, JsonRpcResponse<?>>> listeners = Sets.newConcurrentHashSet();

    public void notifyListeners(String sessionId, JsonRpcResponse<?> rpcResponse)
    {
        listeners.forEach(listener -> listener.accept(sessionId, rpcResponse));
    }

    public void addListener(BiConsumer<String, JsonRpcResponse<?>> listener)
    {
        listeners.add(listener);
    }

    public void removeListener(BiConsumer<String, JsonRpcResponse<?>> listener)
    {
        listeners.remove(listener);
    }
}

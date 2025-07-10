package io.airlift.jsonrpc;

import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import io.airlift.jsonrpc.binding.InternalRpcModule;

import java.util.function.Consumer;

public interface JsonRpcModule
{
    static Builder<?> builder()
    {
        return InternalRpcModule.builder();
    }

    interface Builder<T extends Builder<T>>
    {
        T withBasePath(String basePath);

        T add(JsonRpcMethod jsonRpcMethod);

        T withRequestFilter(Consumer<LinkedBindingBuilder<JsonRpcRequestFilter>> binding);

        Module build();
    }
}

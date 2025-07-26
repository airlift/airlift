package io.airlift.jsonrpc;

import com.google.inject.Module;
import io.airlift.jsonrpc.binding.InternalRpcModule;

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

        Module build();
    }
}

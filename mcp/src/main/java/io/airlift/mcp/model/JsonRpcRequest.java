package io.airlift.mcp.model;

import jakarta.annotation.Nullable;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

// see https://www.jsonrpc.org/specification#request_object
public record JsonRpcRequest<T>(String jsonrpc, @Nullable Object id, String method, Optional<T> params)
{
    public static final String JSON_RPC_VERSION = "2.0";

    public JsonRpcRequest
    {
        requireNonNull(jsonrpc, "jsonrpc is null");
        requireNonNull(method, "method is null");
        requireNonNull(params, "params is null");
    }

    public static <T> JsonRpcRequest<T> buildRequest(@Nullable Object id, String method, T params)
    {
        return new JsonRpcRequest<>(JSON_RPC_VERSION, id, method, Optional.of(params));
    }

    public static <T> JsonRpcRequest<T> buildRequest(@Nullable Object id, String method)
    {
        return new JsonRpcRequest<>(JSON_RPC_VERSION, id, method, Optional.empty());
    }

    public static <T> JsonRpcRequest<T> buildNotification(String method, T params)
    {
        return new JsonRpcRequest<>(JSON_RPC_VERSION, null, method, Optional.of(params));
    }

    public static <T> JsonRpcRequest<T> buildNotification(String method)
    {
        return new JsonRpcRequest<>(JSON_RPC_VERSION, null, method, Optional.empty());
    }
}

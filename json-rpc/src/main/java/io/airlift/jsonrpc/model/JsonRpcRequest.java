package io.airlift.jsonrpc.model;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.json.JsonMapperProvider;

import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

// see https://www.jsonrpc.org/specification#request_object
public record JsonRpcRequest<T>(String jsonrpc, Object id, String method, Optional<T> params)
        implements JsonRpcMessage
{
    static final JsonMapper MAPPER = new JsonMapperProvider().get();

    public JsonRpcRequest
    {
        jsonrpc = requireNonNullElse(jsonrpc, "");
        method = requireNonNullElse(method, "");
        params = requireNonNullElse(params, Optional.empty());
    }

    public static <T> JsonRpcRequest<T> buildRequest(Object id, String method, T params)
    {
        return new JsonRpcRequest<>(JSON_RPC_VERSION, id, method, Optional.of(params));
    }

    public static <T> JsonRpcRequest<T> buildRequest(Object id, String method)
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

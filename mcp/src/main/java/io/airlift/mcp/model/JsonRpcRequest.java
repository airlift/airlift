package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.json.ObjectMapperProvider;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.mcp.model.Constants.JSON_RPC_VERSION;

// see https://www.jsonrpc.org/specification#request_object
public record JsonRpcRequest<T>(String jsonrpc, Object id, String method, Optional<T> params)
        implements JsonRpcMessage
{
    static final ObjectMapper MAPPER = new ObjectMapperProvider().get();

    public JsonRpcRequest
    {
        jsonrpc = firstNonNull(jsonrpc, "");
        method = firstNonNull(method, "");
        params = firstNonNull(params, Optional.empty());
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

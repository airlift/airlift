package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.mcp.model.Constants.JSON_RPC_VERSION;

// see https://www.jsonrpc.org/specification#response_object
public record JsonRpcResponse<T>(String jsonrpc, Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
        implements JsonRpcMessage
{
    public JsonRpcResponse
    {
        jsonrpc = firstNonNull(jsonrpc, "");
        id = firstNonNull(id, "");
        error = firstNonNull(error, Optional.empty());
        result = firstNonNull(result, Optional.empty());
    }

    public JsonRpcResponse(Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
    {
        this(JSON_RPC_VERSION, id, error, result);
    }

    public <R> JsonRpcResponse<R> map(Class<R> responseType, ObjectMapper objectMapper)
    {
        return result().map(value -> {
            R convertedValue = objectMapper.convertValue(value, responseType);
            return new JsonRpcResponse<>(id(), Optional.empty(), Optional.of(convertedValue));
        }).orElseGet(() -> new JsonRpcResponse<>(id(), error, Optional.empty()));
    }
}

package io.airlift.mcp.model;

import jakarta.annotation.Nullable;

import java.util.Optional;

import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static java.util.Objects.requireNonNull;

// see https://www.jsonrpc.org/specification#response_object
public record JsonRpcResponse<T>(String jsonrpc, @Nullable Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
{
    public JsonRpcResponse
    {
        requireNonNull(jsonrpc, "jsonrpc is null");
        requireNonNull(error, "error is null");
        requireNonNull(result, "result is null");
    }

    public JsonRpcResponse(@Nullable Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
    {
        this(JSON_RPC_VERSION, id, error, result);
    }
}

package io.airlift.mcp.model;

import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static java.util.Objects.requireNonNull;

import java.util.Optional;

// see https://www.jsonrpc.org/specification#response_object
public record JsonRpcResponse<T>(String jsonrpc, Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result) {
    public JsonRpcResponse {
        requireNonNull(jsonrpc, "jsonrpc is null");
        requireNonNull(error, "error is null");
        requireNonNull(result, "result is null");
    }

    public JsonRpcResponse(Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result) {
        this(JSON_RPC_VERSION, id, error, result);
    }
}

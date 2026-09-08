package io.airlift.mcp.model;

import java.util.Optional;

import static io.airlift.mcp.model.Constants.JSON_RPC_VERSION;
import static java.util.Objects.requireNonNullElse;

// see https://www.jsonrpc.org/specification#response_object
public record JsonRpcResponse<T>(String jsonrpc, Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
        implements JsonRpcMessage
{
    public JsonRpcResponse
    {
        jsonrpc = requireNonNullElse(jsonrpc, "");
        id = requireNonNullElse(id, "");
        error = requireNonNullElse(error, Optional.empty());
        result = requireNonNullElse(result, Optional.empty());
    }

    public JsonRpcResponse(Object id, Optional<JsonRpcErrorDetail> error, Optional<T> result)
    {
        this(JSON_RPC_VERSION, id, error, result);
    }
}

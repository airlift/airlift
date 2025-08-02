package io.airlift.jsonrpc.model;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.Optional;

import static io.airlift.jsonrpc.JsonRpc.JSON_RPC_VERSION;
import static io.airlift.jsonrpc.binding.InternalRpcFilter.requestId;
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

    public static <T> JsonRpcResponse<T> buildResponse(Request request, T result)
    {
        Object requestId = (request instanceof ContainerRequest containerRequest) ? requestId(containerRequest).orElse(null) : null;
        return new JsonRpcResponse<>(JSON_RPC_VERSION, requestId, Optional.empty(), Optional.of(result));
    }

    public static <T> JsonRpcResponse<T> buildErrorResponse(Request request, JsonRpcErrorDetail error)
    {
        Object requestId = (request instanceof ContainerRequest containerRequest) ? requestId(containerRequest).orElse(null) : null;
        return new JsonRpcResponse<>(JSON_RPC_VERSION, requestId, Optional.of(error), Optional.empty());
    }
}

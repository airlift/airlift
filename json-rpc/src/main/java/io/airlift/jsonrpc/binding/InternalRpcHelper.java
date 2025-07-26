package io.airlift.jsonrpc.binding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.jsonrpc.JsonRpc;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

class InternalRpcHelper
{
    private final ObjectMapper objectMapper;
    private final Provider<RpcMetadata> jsonRpcMetadata;

    @Inject
    InternalRpcHelper(ObjectMapper objectMapper, Provider<RpcMetadata> jsonRpcMetadata)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.jsonRpcMetadata = requireNonNull(jsonRpcMetadata, "jsonRpcMetadata is null");
    }

    boolean isPotentialJsonRpc(ContainerRequestContext requestContext)
    {
        if (requestContext.getRequest().getMethod().equalsIgnoreCase("POST")) {
            String rpcPath = requestContext.getUriInfo().getBaseUri().resolve(jsonRpcMetadata.get().basePath()).getRawPath();
            String requestPath = requestContext.getUriInfo().getRequestUri().getRawPath();
            return requestPath.equals(rpcPath);
        }
        return false;
    }

    RpcMetadata jsonRpcMetadata()
    {
        return jsonRpcMetadata.get();
    }

    Object rpcError(@Nullable Object id, JsonRpcErrorCode errorCode, String message, Optional<Object> data)
    {
        return rpcError(id, errorCode.code(), message, data);
    }

    Object rpcError(@Nullable Object id, int errorCode, String message, Optional<Object> data)
    {
        ObjectNode error = objectMapper.createObjectNode()
                .put("code", errorCode)
                .put("message", message);
        data.ifPresent(d -> error.putPOJO("data", d));
        return objectMapper.createObjectNode()
                .put("jsonrpc", JsonRpc.JSON_RPC_VERSION)
                .putPOJO("id", id)
                .set("error", error);
    }

    Object rpcResponse(@Nullable Object id, @Nullable Object result)
    {
        return objectMapper.createObjectNode()
                .put("jsonrpc", JsonRpc.JSON_RPC_VERSION)
                .putPOJO("id", id)
                .putPOJO("result", result);
    }

    WebApplicationException rpcException(@Nullable Object id, JsonRpcErrorCode errorCode, String message, Optional<Object> data)
    {
        Object error = rpcError(id, errorCode, message, data);
        return new WebApplicationException(message, Response.ok(error).build());
    }

    InternalRequest buildRequest(InputStream inputStream)
    {
        JsonNode tree = parseStream(inputStream);
        JsonNode idNode = tree.get("id");
        Object id;
        if (idNode == null) {
            id = null;
        }
        else if (idNode.isNumber()) {
            id = idNode.asLong();
        }
        else {
            id = idNode.asText();
        }

        JsonNode jsonrpcNode = tree.get("jsonrpc");
        if (jsonrpcNode == null) {
            throw rpcException(id, INVALID_REQUEST, "Missing \"jsonrpc\" field", Optional.empty());
        }
        String jsonrpcVersion = jsonrpcNode.asText();
        if (!JsonRpc.JSON_RPC_VERSION.equals(jsonrpcVersion)) {
            throw rpcException(id, INVALID_REQUEST, "Invalid \"jsonrpc\": " + jsonrpcVersion, Optional.empty());
        }

        JsonNode methodNode = tree.get("method");
        if (methodNode == null) {
            throw rpcException(id, INVALID_REQUEST, "Missing \"method\" field", Optional.empty());
        }
        String method = methodNode.asText();

        Optional<JsonNode> params = Optional.ofNullable(tree.get("params"));
        Optional<InputStream> paramsInputStream = params.map(paramNode -> {
            // NOTE: this should be relatively efficient for most requests. However, very large
            // requests imply the overhead of the ByteArrayInputStream. JSON-RPC isn't usually
            // used for very large requests.
            try {
                return new ByteArrayInputStream(objectMapper.writeValueAsBytes(paramNode));
            }
            catch (JsonProcessingException e) {
                throw rpcException(id, INVALID_PARAMS, "Could not parse \"params\": " + e.getMessage(), Optional.empty());
            }
        });
        return new InternalRequest(id, method, params, paramsInputStream);
    }

    private JsonNode parseStream(InputStream inputStream)
    {
        try {
            return objectMapper.readTree(inputStream);
        }
        catch (IOException e) {
            throw rpcException(null, INVALID_REQUEST, firstNonNull(e.getMessage(), "Invalid JSON request"), Optional.empty());
        }
    }
}

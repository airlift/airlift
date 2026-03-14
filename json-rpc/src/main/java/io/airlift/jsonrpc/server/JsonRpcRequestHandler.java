package io.airlift.jsonrpc.server;

import io.airlift.jsonrpc.model.JsonRpcMessage;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

@FunctionalInterface
public interface JsonRpcRequestHandler
            extends JsonRpcHandler
{
    @Override
    default void handle(JsonRpcRequestContext requestContext, HttpServletRequest request, HttpServletResponse response)
    {
        handle(requestContext, requestContext.jsonRpcMessage(), request, response)
                .ifPresent(jsonRpcResponse -> JsonRpcMessage.writeJsonRpcResponse(requestContext, jsonRpcResponse));
    }

    Optional<JsonRpcResponse<?>> handle(JsonRpcRequestContext requestContext, JsonRpcMessage jsonRpcMessage, HttpServletRequest request, HttpServletResponse response);
}

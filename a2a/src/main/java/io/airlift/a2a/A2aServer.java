package io.airlift.a2a;

import io.airlift.jsonrpc.model.JsonRpcMessage;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.jsonrpc.server.JsonRpcRequestContext;
import io.airlift.jsonrpc.server.JsonRpcRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

public class A2aServer
        implements JsonRpcRequestHandler
{
    // see https://a2a-protocol.org/latest/specification

    public static A2aRequestContext requestContext(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getAttribute(A2aRequestContext.class.getName()))
                .map(A2aRequestContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("A2aRequestContext not found in request attributes"));
    }

    @Override
    public Optional<JsonRpcResponse<?>> handle(JsonRpcRequestContext requestContext, JsonRpcMessage jsonRpcMessage, HttpServletRequest request, HttpServletResponse response)
    {
        A2aRequestContext a2aRequestContext = new A2aRequestContext() {};
        request.setAttribute(A2aRequestContext.class.getName(), a2aRequestContext);

        if (jsonRpcMessage instanceof JsonRpcRequest<?> jsonRpcRequest) {
            switch (jsonRpcRequest.method()) {
                case "SendMessage" -> {
                }

                case "SendStreamingMessage" -> {
                }

                case "GetTask" -> {
                }

                case "ListTasks" -> {
                }

                case "CancelTask" -> {
                }

                case "SubscribeToTask" -> {
                }

                case "GetExtendedAgentCard" -> {
                }
            }
        }
        return Optional.empty();
    }
}

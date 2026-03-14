package io.airlift.jsonrpc.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.airlift.jsonrpc.server.JsonRpcException;
import io.airlift.jsonrpc.server.JsonRpcRequestContext;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.PARSE_ERROR;
import static io.airlift.jsonrpc.model.JsonRpcRequest.MAPPER;
import static io.airlift.jsonrpc.server.JsonRpcException.exception;
import static java.util.stream.Collectors.joining;

public sealed interface JsonRpcMessage
        permits JsonRpcRequest, JsonRpcResponse
{
    String JSON_RPC_VERSION = "2.0";

    String jsonrpc();

    Object id();

    static void writeJsonRpcResponse(JsonRpcRequestContext requestContext, JsonRpcResponse<?> jsonRpcResponse)
    {
        try {
            requestContext.sseMessageWriter().write(requestContext.jsonMapper().writeValueAsString(jsonRpcResponse));
            requestContext.sseMessageWriter().flushMessages();
        }
        catch (JsonProcessingException e) {
            // TODO
            throw new UncheckedIOException(e);
        }
    }

    @JsonCreator
    static JsonRpcMessage deserialize(Map<String, Object> map)
    {
        if (map.containsKey("result") || map.containsKey("error")) {
            return MAPPER.convertValue(map, JsonRpcResponse.class);
        }
        return MAPPER.convertValue(map, JsonRpcRequest.class);
    }

    static JsonRpcMessage readRequestMessage(JsonRpcRequestContext requestContext, HttpServletRequest request)
    {
        try (BufferedReader reader = request.getReader()) {
            String body = reader.lines().collect(joining("\n"));
            return deserializeFromJson(requestContext, body);
        }
        catch (IOException e) {
            JsonRpcException exception = exception(PARSE_ERROR, "Cannot deserialize JsonRpcMessage", e);
            exception.initCause(e);
            throw exception;
        }
    }

    static JsonRpcMessage deserializeFromJson(JsonRpcRequestContext requestContext, String json)
            throws IOException
    {
        JsonNode tree = requestContext.jsonMapper().readTree(json);

        if (tree.has("method")) {
            return requestContext.jsonMapper().convertValue(tree, JsonRpcRequest.class);
        }

        if (tree.has("result") || tree.has("error")) {
            return requestContext.jsonMapper().convertValue(tree, JsonRpcResponse.class);
        }

        throw exception(PARSE_ERROR, "Cannot deserialize JsonRpcMessage: " + json);
    }
}

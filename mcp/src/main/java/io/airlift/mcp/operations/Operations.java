package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface Operations
{
    void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest);

    void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest);

    void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, JsonRpcResponse<?> rpcResponse);

    void handleRcpDeleteRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated);

    void handleRpcGetRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated);

    static <T> T convertParams(JsonMapper jsonMapper, JsonRpcRequest<?> rpcRequest, Class<T> clazz)
    {
        Object value = rpcRequest.params().map(v -> (Object) v).orElseGet(ImmutableMap::of);
        return jsonMapper.convertValue(value, clazz);
    }
}

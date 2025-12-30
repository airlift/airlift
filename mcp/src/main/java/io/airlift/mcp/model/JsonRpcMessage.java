package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

import static io.airlift.mcp.model.JsonRpcRequest.MAPPER;

public sealed interface JsonRpcMessage
        permits JsonRpcRequest, JsonRpcResponse
{
    String jsonrpc();

    Object id();

    @JsonCreator
    static JsonRpcMessage deserialize(Map<String, Object> map)
    {
        if (map.containsKey("result") || map.containsKey("error")) {
            return MAPPER.convertValue(map, JsonRpcResponse.class);
        }
        return MAPPER.convertValue(map, JsonRpcRequest.class);
    }
}

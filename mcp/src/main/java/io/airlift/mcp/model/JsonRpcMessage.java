package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.json.ObjectMapperProvider;

import java.util.Map;

public sealed interface JsonRpcMessage
        permits JsonRpcRequest, JsonRpcResponse
{
    ObjectMapper MAPPER = new ObjectMapperProvider().get();

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

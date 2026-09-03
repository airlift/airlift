package io.airlift.mcp.client.settings;

import io.airlift.http.client.Response;
import io.airlift.mcp.model.JsonRpcResponse;

public interface ResponseFilter
{
    JsonRpcResponse<Object> apply(Response response, JsonRpcResponse<Object> rpcResponse);

    default ResponseFilter andThen(ResponseFilter after)
    {
        return (response, rpcResponse) -> after.apply(response, apply(response, rpcResponse));
    }
}

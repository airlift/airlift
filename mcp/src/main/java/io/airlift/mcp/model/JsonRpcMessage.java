package io.airlift.mcp.model;

public sealed interface JsonRpcMessage
        permits JsonRpcRequest, JsonRpcResponse
{
    Object id();
}

package io.airlift.mcp.features;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.Protocol;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public record ParsedRpcMessage(JsonRpcMessage message, Protocol protocol, Features features, Function<McpRequestContext, ?> feature)
{
    public ParsedRpcMessage
    {
        requireNonNull(message, "message is null");
        requireNonNull(protocol, "protocol is null");
        requireNonNull(feature, "feature is null");
    }
}

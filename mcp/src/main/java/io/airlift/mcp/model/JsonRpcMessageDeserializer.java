package io.airlift.mcp.model;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class JsonRpcMessageDeserializer
        extends ValueDeserializer<JsonRpcMessage>
{
    @Override
    public JsonRpcMessage deserialize(JsonParser parser, DeserializationContext context)
    {
        JsonNode tree = context.readTree(parser);

        if (tree.has("result") || tree.has("error")) {
            return context.readTreeAsValue(tree, JsonRpcResponse.class);
        }

        return context.readTreeAsValue(tree, JsonRpcRequest.class);
    }
}

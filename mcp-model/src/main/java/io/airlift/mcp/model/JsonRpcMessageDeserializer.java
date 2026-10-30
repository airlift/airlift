package io.airlift.mcp.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class JsonRpcMessageDeserializer
        extends StdDeserializer<JsonRpcMessage>
{
    public JsonRpcMessageDeserializer()
    {
        super(JsonRpcMessage.class);
    }

    @Override
    public JsonRpcMessage deserialize(JsonParser parser, DeserializationContext context)
            throws IOException
    {
        JsonNode tree = context.readTree(parser);

        if (tree.has("result") || tree.has("error")) {
            return context.readTreeAsValue(tree, JsonRpcResponse.class);
        }

        return context.readTreeAsValue(tree, JsonRpcRequest.class);
    }
}

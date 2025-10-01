package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.ImmutableMap;
import io.modelcontextprotocol.spec.McpError;

import java.io.IOException;

public final class McpErrorSerializer
        extends JsonSerializer<McpError>
{
    @Override
    public void serialize(McpError value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException
    {
        if (value == null) {
            gen.writeNull();
        }
        else if (value.getJsonRpcError() != null) {
            gen.writeObject(value.getJsonRpcError());
        }
        else {
            gen.writeObject(ImmutableMap.of("message", value.getMessage()));
        }
    }
}

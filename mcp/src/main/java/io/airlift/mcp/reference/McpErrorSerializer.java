package io.airlift.mcp.reference;

import com.google.common.collect.ImmutableMap;
import io.modelcontextprotocol.spec.McpError;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class McpErrorSerializer
        extends ValueSerializer<McpError>
{
    @Override
    public void serialize(McpError value, JsonGenerator gen, SerializationContext context)
    {
        if (value == null) {
            gen.writeNull();
        }
        else if (value.getJsonRpcError() != null) {
            gen.writePOJO(value.getJsonRpcError());
        }
        else {
            gen.writePOJO(ImmutableMap.of("message", value.getMessage()));
        }
    }
}

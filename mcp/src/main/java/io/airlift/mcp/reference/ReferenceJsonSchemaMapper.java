package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.airlift.mcp.McpJsonSchemaMapper;
import io.airlift.mcp.model.JsonSchema;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ReferenceJsonSchemaMapper
        implements McpJsonSchemaMapper
{
    private final McpJsonMapper mcpJsonMapper;

    @Inject
    public ReferenceJsonSchemaMapper(McpJsonMapper mcpJsonMapper)
    {
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "mcpJsonMapper is null");
    }

    @Override
    public JsonSchema toJsonSchema(Class<?> type)
    {
        ObjectNode objectNode = new JsonSchemaBuilder(type.getName()).build(Optional.empty(), type);
        return mcpJsonMapper.convertValue(objectNode, new TypeRef<>() {});
    }
}

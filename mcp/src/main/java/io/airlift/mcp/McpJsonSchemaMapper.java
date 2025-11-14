package io.airlift.mcp;

import io.airlift.mcp.model.JsonSchema;

public interface McpJsonSchemaMapper
{
    JsonSchema toJsonSchema(Class<?> type);
}

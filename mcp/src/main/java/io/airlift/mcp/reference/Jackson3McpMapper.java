package io.airlift.mcp.reference;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import static java.util.Objects.requireNonNull;

// Adapts McpJsonMapper to Jackson 3.x new coordinates
public record Jackson3McpMapper(ObjectMapper objectMapper)
        implements McpJsonMapper
{
    public Jackson3McpMapper
    {
        requireNonNull(objectMapper, "objectMapper is null");
    }

    public <T> T readValue(String content, Class<T> type)
    {
        return (T) this.objectMapper.readValue(content, type);
    }

    public <T> T readValue(byte[] content, Class<T> type)
    {
        return (T) this.objectMapper.readValue(content, type);
    }

    public <T> T readValue(String content, TypeRef<T> type)
    {
        JavaType javaType = this.objectMapper.getTypeFactory().constructType(type.getType());
        return (T) this.objectMapper.readValue(content, javaType);
    }

    public <T> T readValue(byte[] content, TypeRef<T> type)
    {
        JavaType javaType = this.objectMapper.getTypeFactory().constructType(type.getType());
        return (T) this.objectMapper.readValue(content, javaType);
    }

    public <T> T convertValue(Object fromValue, Class<T> type)
    {
        return (T) this.objectMapper.convertValue(fromValue, type);
    }

    public <T> T convertValue(Object fromValue, TypeRef<T> type)
    {
        JavaType javaType = this.objectMapper.getTypeFactory().constructType(type.getType());
        return (T) this.objectMapper.convertValue(fromValue, javaType);
    }

    public String writeValueAsString(Object value)
    {
        return this.objectMapper.writeValueAsString(value);
    }

    public byte[] writeValueAsBytes(Object value)
    {
        return this.objectMapper.writeValueAsBytes(value);
    }
}

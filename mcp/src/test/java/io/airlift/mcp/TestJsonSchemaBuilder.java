package io.airlift.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.reflection.MethodParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.StreamSupport;

import static io.airlift.mcp.model.JsonSchemaBuilder.isPrimitiveType;
import static io.airlift.mcp.model.JsonSchemaBuilder.isSupportedType;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonSchemaBuilder
{
    private final JsonSchemaBuilder jsonSchemaBuilder = new JsonSchemaBuilder();

    public record BasicRecord(String name, int qty, List<String> tags) {}

    public record RecursiveRecord(String name, int qty, List<RecursiveRecord> records) {}

    public record DescribedRecord(
            @McpDescription("this is an int") int i,
            @McpDescription("this might be an int") OptionalInt optInt,
            @McpDescription("this is a long") long l,
            @McpDescription("this might be a long") OptionalLong optLong,
            @McpDescription("this is a double") double d,
            @McpDescription("this might be a double") OptionalDouble optDouble,
            @McpDescription("this is a string") String s,
            @McpDescription("this might be a string") Optional<String> optStr) {}

    public record EmptyRecord() {}

    public record NestedRecord(BasicRecord inner, String label) {}

    public record RecordWithOptionalNaked(String name, Optional<String> nickname) {}

    public record RecordWithBoolean(boolean active, Boolean verified) {}

    public enum Color { RED, GREEN, BLUE }

    public record RecordWithEnum(Color color, String label) {}

    public record RecordWithMap(Map<String, String> metadata, String name) {}

    @Test
    public void testPrimitives()
    {
        assertSchema(short.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Short.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(int.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Integer.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(BigInteger.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(long.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Long.class, "{\"type\":\"integer\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(double.class, "{\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Double.class, "{\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(BigDecimal.class, "{\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(float.class, "{\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Float.class, "{\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(boolean.class, "{\"type\":\"boolean\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(Boolean.class, "{\"type\":\"boolean\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(String.class, "{\"type\":\"string\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
    }

    @Test
    public void testPrimitiveWithDescription()
    {
        assertSchema(String.class, Optional.of("A name field"), "{\"type\":\"string\",\"description\":\"A name field\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(int.class, Optional.of("A count"), "{\"type\":\"integer\",\"description\":\"A count\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(boolean.class, Optional.of("Is active"), "{\"type\":\"boolean\",\"description\":\"Is active\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(double.class, Optional.of("A ratio"), "{\"type\":\"number\",\"description\":\"A ratio\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
    }

    @Test
    public void testRecords()
    {
        assertSchema(BasicRecord.class, "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"qty\":{\"type\":\"integer\"},\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"name\",\"qty\",\"tags\"],\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(RecursiveRecord.class, "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"qty\":{\"type\":\"integer\"},\"records\":{\"type\":\"array\",\"items\":{\"$ref\":\"#\"}}},\"required\":[\"name\",\"qty\",\"records\"],\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        assertSchema(DescribedRecord.class, Optional.of("It has been described"), "{\"type\":\"object\",\"properties\":{\"d\":{\"type\":\"number\",\"description\":\"this is a double\"},\"i\":{\"type\":\"integer\",\"description\":\"this is an int\"},\"l\":{\"type\":\"integer\",\"description\":\"this is a long\"},\"optDouble\":{\"type\":\"object\",\"properties\":{\"isPresent\":{\"type\":\"boolean\"},\"value\":{\"type\":\"number\"}},\"required\":[\"isPresent\",\"value\"],\"description\":\"this might be a double\"},\"optInt\":{\"type\":\"object\",\"properties\":{\"isPresent\":{\"type\":\"boolean\"},\"value\":{\"type\":\"integer\"}},\"required\":[\"isPresent\",\"value\"],\"description\":\"this might be an int\"},\"optLong\":{\"type\":\"object\",\"properties\":{\"isPresent\":{\"type\":\"boolean\"},\"value\":{\"type\":\"integer\"}},\"required\":[\"isPresent\",\"value\"],\"description\":\"this might be a long\"},\"optStr\":{\"type\":[\"string\",\"null\"],\"description\":\"this might be a string\"},\"s\":{\"type\":\"string\",\"description\":\"this is a string\"}},\"required\":[\"d\",\"i\",\"l\",\"optDouble\",\"s\"],\"description\":\"It has been described\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
    }

    @Test
    public void testEmptyRecord()
    {
        ObjectNode schema = jsonSchemaBuilder.build(EmptyRecord.class);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    public void testNestedRecord()
    {
        ObjectNode schema = jsonSchemaBuilder.build(NestedRecord.class);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("label")).isTrue();
        assertThat(properties.get("label").get("type").asText()).isEqualTo("string");

        // Nested record should be inlined as an object with its own properties
        ObjectNode innerSchema = (ObjectNode) properties.get("inner");
        assertThat(innerSchema.get("type").asText()).isEqualTo("object");
        ObjectNode innerProperties = (ObjectNode) innerSchema.get("properties");
        assertThat(innerProperties.has("name")).isTrue();
        assertThat(innerProperties.has("qty")).isTrue();
        assertThat(innerProperties.has("tags")).isTrue();

        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactlyInAnyOrder("inner", "label");
    }

    @Test
    public void testRecordWithBooleanFields()
    {
        ObjectNode schema = jsonSchemaBuilder.build(RecordWithBoolean.class);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.get("active").get("type").asText()).isEqualTo("boolean");
        assertThat(properties.get("verified").get("type").asText()).isEqualTo("boolean");

        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactlyInAnyOrder("active", "verified");
    }

    @Test
    public void testRecordWithEnum()
    {
        ObjectNode schema = jsonSchemaBuilder.build(RecordWithEnum.class);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("color")).isTrue();
        assertThat(properties.has("label")).isTrue();
        assertThat(properties.get("label").get("type").asText()).isEqualTo("string");

        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactlyInAnyOrder("color", "label");
    }

    @Test
    public void testRecordWithOptionalWithoutDescription()
    {
        ObjectNode schema = jsonSchemaBuilder.build(RecordWithOptionalNaked.class);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.has("nickname")).isTrue();

        // Optional<String> without description should not have a description field
        assertThat(properties.get("nickname").has("description")).isFalse();

        // name should be required, nickname (Optional) should not
        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactly("name");
    }

    @Test
    public void testRecordWithMap()
    {
        ObjectNode schema = jsonSchemaBuilder.build(RecordWithMap.class);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("metadata")).isTrue();
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.get("name").get("type").asText()).isEqualTo("string");

        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactlyInAnyOrder("metadata", "name");
    }

    @Test
    public void testRecordWithDescription()
    {
        ObjectNode schema = jsonSchemaBuilder.build(Optional.of("A basic record"), BasicRecord.class);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("description").asText()).isEqualTo("A basic record");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.get("properties").has("name")).isTrue();
    }

    @Test
    public void testIsPrimitiveType()
    {
        // All supported primitive types
        assertThat(isPrimitiveType(String.class)).isTrue();
        assertThat(isPrimitiveType(int.class)).isTrue();
        assertThat(isPrimitiveType(Integer.class)).isTrue();
        assertThat(isPrimitiveType(long.class)).isTrue();
        assertThat(isPrimitiveType(Long.class)).isTrue();
        assertThat(isPrimitiveType(short.class)).isTrue();
        assertThat(isPrimitiveType(Short.class)).isTrue();
        assertThat(isPrimitiveType(float.class)).isTrue();
        assertThat(isPrimitiveType(Float.class)).isTrue();
        assertThat(isPrimitiveType(double.class)).isTrue();
        assertThat(isPrimitiveType(Double.class)).isTrue();
        assertThat(isPrimitiveType(boolean.class)).isTrue();
        assertThat(isPrimitiveType(Boolean.class)).isTrue();
        assertThat(isPrimitiveType(BigInteger.class)).isTrue();
        assertThat(isPrimitiveType(BigDecimal.class)).isTrue();

        // Non-primitive types
        assertThat(isPrimitiveType(Object.class)).isFalse();
        assertThat(isPrimitiveType(BasicRecord.class)).isFalse();
        assertThat(isPrimitiveType(List.class)).isFalse();
        assertThat(isPrimitiveType(Map.class)).isFalse();
        assertThat(isPrimitiveType(byte.class)).isFalse();
        assertThat(isPrimitiveType(Byte.class)).isFalse();
        assertThat(isPrimitiveType(char.class)).isFalse();
        assertThat(isPrimitiveType(Color.class)).isFalse();
    }

    @Test
    public void testIsSupportedType()
    {
        // Primitives are supported
        assertThat(isSupportedType(String.class)).isTrue();
        assertThat(isSupportedType(int.class)).isTrue();
        assertThat(isSupportedType(boolean.class)).isTrue();
        assertThat(isSupportedType(double.class)).isTrue();
        assertThat(isSupportedType(BigDecimal.class)).isTrue();

        // Records are supported
        assertThat(isSupportedType(BasicRecord.class)).isTrue();
        assertThat(isSupportedType(EmptyRecord.class)).isTrue();
        assertThat(isSupportedType(NestedRecord.class)).isTrue();

        // Raw collection/map types without type parameters are not supported
        assertThat(isSupportedType(Map.class)).isFalse();
        assertThat(isSupportedType(List.class)).isFalse();
        assertThat(isSupportedType(Set.class)).isFalse();

        // Other non-supported types
        assertThat(isSupportedType(Object.class)).isFalse();
        assertThat(isSupportedType(byte.class)).isFalse();
        assertThat(isSupportedType(Byte.class)).isFalse();
        assertThat(isSupportedType(Color.class)).isFalse();
    }

    @Test
    public void testBuildWithMethodParameters()
    {
        List<MethodParameter> parameters = ImmutableList.of(
                new ObjectParameter("name", String.class, String.class, Optional.empty(), Optional.empty(), true),
                new ObjectParameter("count", int.class, int.class, Optional.empty(), Optional.empty(), true),
                new ObjectParameter("label", String.class, String.class, Optional.empty(), Optional.empty(), false));

        ObjectNode schema = jsonSchemaBuilder.build(Optional.empty(), parameters);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.has("description")).isFalse();

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.get("name").get("type").asText()).isEqualTo("string");
        assertThat(properties.has("count")).isTrue();
        assertThat(properties.get("count").get("type").asText()).isEqualTo("integer");
        assertThat(properties.has("label")).isTrue();
        assertThat(properties.get("label").get("type").asText()).isEqualTo("string");

        // name and count are required, label is not
        ArrayNode required = (ArrayNode) schema.get("required");
        List<String> requiredFields = StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).containsExactly("name", "count");
    }

    @Test
    public void testBuildWithMethodParametersAndDescription()
    {
        List<MethodParameter> parameters = ImmutableList.of(
                new ObjectParameter("id", int.class, int.class, Optional.empty(), Optional.empty(), true));

        ObjectNode schema = jsonSchemaBuilder.build(Optional.of("Tool parameters"), parameters);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("description").asText()).isEqualTo("Tool parameters");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    public void testBuildFiltersNonObjectParameters()
    {
        List<MethodParameter> parameters = ImmutableList.of(
                MethodParameter.HttpRequestParameter.INSTANCE,
                new ObjectParameter("name", String.class, String.class, Optional.empty(), Optional.empty(), true),
                MethodParameter.McpRequestContextParameter.INSTANCE,
                MethodParameter.CallToolRequestParameter.INSTANCE);

        ObjectNode schema = jsonSchemaBuilder.build(Optional.empty(), parameters);
        ObjectNode properties = (ObjectNode) schema.get("properties");

        // Only the ObjectParameter should appear in properties
        assertThat(properties.size()).isEqualTo(1);
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.get("name").get("type").asText()).isEqualTo("string");

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(1);
        assertThat(required.get(0).asText()).isEqualTo("name");
    }

    @Test
    public void testBuildWithEmptyParameters()
    {
        ObjectNode schema = jsonSchemaBuilder.build(Optional.empty(), ImmutableList.of());
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.size()).isEqualTo(0);

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(0);
    }

    @Test
    public void testBuildWithOnlyNonObjectParameters()
    {
        List<MethodParameter> parameters = ImmutableList.of(
                MethodParameter.HttpRequestParameter.INSTANCE,
                MethodParameter.McpRequestContextParameter.INSTANCE);

        ObjectNode schema = jsonSchemaBuilder.build(Optional.empty(), parameters);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertThat(properties.size()).isEqualTo(0);

        ArrayNode required = (ArrayNode) schema.get("required");
        assertThat(required).hasSize(0);
    }

    private void assertSchema(Class<?> type, String expected)
    {
        assertSchema(type, Optional.empty(), expected);
    }

    private void assertSchema(Class<?> type, Optional<String> description, String expected)
    {
        ObjectNode objectNode = jsonSchemaBuilder.build(description, type);
        assertThat(objectNode.toString()).isEqualTo(expected);
    }
}

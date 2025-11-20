package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.TypeLiteral;
import io.airlift.json.ObjectMapperProvider;
import io.airlift.mcp.McpDescription;
import io.airlift.mcp.reflection.MethodParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static io.airlift.mcp.reflection.ReflectionHelper.optionalArgument;
import static java.util.Objects.requireNonNull;

public class JsonSchemaBuilder
{
    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    private static final Map<Class<?>, String> primitiveTypes = ImmutableMap.<Class<?>, String>builder()
            .put(String.class, "string")
            .put(Integer.class, "integer")
            .put(int.class, "integer")
            .put(Boolean.class, "boolean")
            .put(boolean.class, "boolean")
            .put(BigInteger.class, "number")
            .put(BigDecimal.class, "number")
            .put(Short.class, "number")
            .put(short.class, "number")
            .put(Long.class, "number")
            .put(long.class, "number")
            .put(Double.class, "number")
            .put(double.class, "number")
            .put(Float.class, "number")
            .put(float.class, "number")
            .build();

    private final String exceptionContext;
    private final List<Class<?>> parents = new ArrayList<>();

    public static ObjectNode forRecord(Class<?> recordType)
    {
        return new JsonSchemaBuilder(recordType.getName()).build(Optional.empty(), recordType);
    }

    public JsonSchemaBuilder(String exceptionContext)
    {
        this.exceptionContext = requireNonNull(exceptionContext, "exceptionContext is null");
    }

    public ObjectNode build(Optional<String> description, List<MethodParameter> parameters)
    {
        return buildObject(description, (properties, required) -> parameters.stream()
                .flatMap(methodParameter -> (methodParameter instanceof ObjectParameter objectParameter) ? Stream.of(objectParameter) : Stream.empty())
                .forEach(objectParameter -> {
                    ObjectNode typeNode;
                    Class<?> rawType = objectParameter.rawType();
                    if (Optional.class.isAssignableFrom(rawType)) {
                        Type genericType = optionalArgument(objectParameter.genericType())
                                .orElseThrow(() -> exception("Optional record component isn't fully declared: " + objectParameter.name()));
                        rawType = TypeLiteral.get(genericType).getRawType();
                    }
                    if (rawType.isRecord()) {
                        typeNode = buildObject(objectParameter.description(), (objectProperties, objectRequried) ->
                                buildRecord(objectParameter.rawType(), objectProperties, objectRequried));
                    }
                    else {
                        typeNode = objectMapper.createObjectNode();
                        typeNode.put("type", primitiveType(rawType));
                        description.ifPresent(value -> typeNode.put("description", value));
                    }

                    properties.set(objectParameter.name(), typeNode);

                    if (objectParameter.required()) {
                        required.add(objectParameter.name());
                    }
                }));
    }

    public ObjectNode build(Optional<String> description, Class<?> recordType)
    {
        return buildObject(description, (objectProperties, objectRequried) ->
                buildRecord(recordType, objectProperties, objectRequried));
    }

    private void buildRecord(Class<?> recordType, ObjectNode properties, ArrayNode required)
    {
        if (parents.contains(recordType)) {
            throw exception("Recursive type detected. Chain: " + parents);
        }
        parents.add(recordType);

        try {
            RecordComponent[] recordComponents = recordType.getRecordComponents();
            for (RecordComponent recordComponent : recordComponents) {
                Class<?> rawType;
                Type genericType;
                String name = recordComponent.getName();

                if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                    genericType = optionalArgument(recordComponent.getGenericType())
                            .orElseThrow(() -> exception("Optional record component isn't fully declared: " + name));
                    rawType = TypeLiteral.get(genericType).getRawType();
                }
                else {
                    required.add(name);
                    rawType = recordComponent.getType();
                    genericType = recordComponent.getGenericType();
                }

                Optional<String> description = Optional.ofNullable(recordComponent.getAnnotation(McpDescription.class))
                        .map(McpDescription::value);
                ObjectNode typeNode = convertType(name, description, genericType, rawType);

                properties.set(name, typeNode);
            }
        }
        finally {
            parents.removeLast();
        }
    }

    public static boolean isPrimitiveType(Type type)
    {
        if (type instanceof Class<?> rawType) {
            return primitiveTypes.containsKey(rawType);
        }
        return false;
    }

    public static boolean isSupportedType(Type type)
    {
        if (type instanceof Class<?> rawType) {
            return primitiveTypes.containsKey(rawType)
                    || rawType.isRecord()
                    || (Map.class.isAssignableFrom(rawType) && isSupportedMap(type))
                    || (Collection.class.isAssignableFrom(rawType) && listArgument(type).map(JsonSchemaBuilder::isSupportedType).orElse(false));
        }
        return false;
    }

    private static boolean isSupportedMap(Type genericType)
    {
        return (genericType instanceof ParameterizedType parameterizedType) && parameterizedType.getActualTypeArguments()[0].equals(String.class)
                && (parameterizedType.getActualTypeArguments().length == 2)
                && parameterizedType.getActualTypeArguments()[1].equals(String.class);
    }

    private ObjectNode convertType(String name, Optional<String> description, Type genericType, Class<?> rawType)
    {
        ObjectNode typeNode;
        if (rawType.isRecord()) {
            typeNode = buildObject(description, (objectProperties, objectRequired) ->
                    buildRecord(rawType, objectProperties, objectRequired));
        }
        else if (Map.class.isAssignableFrom(rawType)) {
            if (!isSupportedMap(genericType)) {
                throw exception("Map types for JSON schema must be Map<String, String>");
            }
            typeNode = buildMap(description);
        }
        else if (Collection.class.isAssignableFrom(rawType)) {
            Type collectionType = listArgument(genericType)
                    .orElseThrow(() -> exception("Collection record component isn't fully declared: " + name));
            typeNode = buildArray(description, collectionType);
        }
        else {
            typeNode = objectMapper.createObjectNode();
            typeNode.put("type", primitiveType(rawType));
            description.ifPresent(value -> typeNode.put("description", value));
        }
        return typeNode;
    }

    private ObjectNode buildArray(Optional<String> description, Type genericType)
    {
        Class<?> rawType = TypeLiteral.get(genericType).getRawType();
        ObjectNode objectNode = convertType("[]", Optional.empty(), genericType, rawType);

        ObjectNode typeNode = objectMapper.createObjectNode();
        typeNode.put("type", "array");
        typeNode.set("items", objectNode);
        description.ifPresent(value -> typeNode.put("description", value));
        return typeNode;
    }

    private ObjectNode buildMap(Optional<String> description)
    {
        ObjectNode additionalPropertiesNode = objectMapper.createObjectNode();
        additionalPropertiesNode.put("type", "string");

        ObjectNode typeNode = objectMapper.createObjectNode();
        typeNode.put("type", "object");
        typeNode.set("additionalProperties", additionalPropertiesNode);
        description.ifPresent(value -> typeNode.put("description", value));

        return typeNode;
    }

    private ObjectNode buildObject(Optional<String> description, BiConsumer<ObjectNode, ArrayNode> propertiesConsumer)
    {
        ArrayNode requiredNode = objectMapper.createArrayNode();
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        propertiesConsumer.accept(propertiesNode, requiredNode);

        ObjectNode objectNode = objectMapper.createObjectNode();
        description.ifPresent(value -> objectNode.put("description", value));
        objectNode.put("type", "object");
        objectNode.set("properties", propertiesNode);
        objectNode.set("required", requiredNode);
        return objectNode;
    }

    private String primitiveType(Class<?> rawType)
    {
        return Optional.ofNullable(primitiveTypes.get(rawType))
                .orElseThrow(() -> exception("Unsupported primitive type: " + rawType));
    }

    private RuntimeException exception(String message)
    {
        return new IllegalArgumentException(message + " at " + exceptionContext);
    }
}

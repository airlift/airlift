package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpDescription;
import io.airlift.mcp.reflection.MethodParameter;
import io.airlift.mcp.reflection.MethodParameter.ObjectParameter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.airlift.mcp.reflection.ReflectionHelper.listArgument;
import static java.util.Objects.requireNonNull;

public class JsonSchemaBuilder
{
    private static final JsonMapper defaultJsonMapper = new JsonMapperProvider().get();

    private static final SchemaGenerator defaultGenerator;

    static {
        defaultGenerator = buildGenerator(defaultJsonMapper);
    }

    private static SchemaGenerator buildGenerator(JsonMapper mapper)
    {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .withObjectMapper(mapper)
                .without(Option.SCHEMA_VERSION_INDICATOR);
        configBuilder.forFields()
                .withDescriptionResolver(JsonSchemaBuilder::findDescription)
                .withRequiredCheck(target -> {
                    boolean isOptional = Optional.class.equals(target.getDeclaredType().getErasedType());
                    boolean isOptionalInt = OptionalInt.class.equals(target.getDeclaredType().getErasedType());
                    boolean isOptionalLong = OptionalLong.class.equals(target.getDeclaredType().getErasedType());
                    boolean isOptionalDouble = OptionalDouble.class.equals(target.getDeclaredType().getErasedType());
                    return !isOptional && !isOptionalInt && !isOptionalLong && !isOptionalDouble;
                });
        SchemaGeneratorConfig config = configBuilder.build();
        return new SchemaGenerator(config);
    }

    private static String findDescription(FieldScope target)
    {
        McpDescription mcpDescription = target.getAnnotation(McpDescription.class);
        if ((mcpDescription == null) && (target.getRawMember().getDeclaringClass().getRecordComponents() != null)) {
            mcpDescription = Stream.of(target.getRawMember().getDeclaringClass().getRecordComponents())
                    .filter(component -> component.getName().equals(target.getName()))
                    .findFirst()
                    .flatMap(component -> Optional.ofNullable(component.getAnnotation(McpDescription.class)))
                    .orElse(null);
        }
        return (mcpDescription != null) ? mcpDescription.value() : null;
    }

    private static final Set<Class<?>> primitiveTypes = ImmutableSet.<Class<?>>builder()
            .add(String.class)
            .add(Integer.class)
            .add(int.class)
            .add(Boolean.class)
            .add(boolean.class)
            .add(BigInteger.class)
            .add(BigDecimal.class)
            .add(Short.class)
            .add(short.class)
            .add(Long.class)
            .add(long.class)
            .add(Double.class)
            .add(double.class)
            .add(Float.class)
            .add(float.class)
            .build();

    private final JsonMapper jsonMapper;
    private final SchemaGenerator generator;

    public JsonSchemaBuilder()
    {
        this(defaultJsonMapper, defaultGenerator);
    }

    public JsonSchemaBuilder(JsonMapper jsonMapper)
    {
        this(jsonMapper, buildGenerator(jsonMapper));
    }

    private JsonSchemaBuilder(JsonMapper jsonMapper, SchemaGenerator generator)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.generator = requireNonNull(generator, "generator is null");
    }

    public ObjectNode build(Optional<String> description, List<MethodParameter> parameters)
    {
        return buildObject(description, (properties, required) -> parameters.stream()
                .flatMap(methodParameter -> (methodParameter instanceof ObjectParameter objectParameter) ? Stream.of(objectParameter) : Stream.empty())
                .forEach(objectParameter -> {
                    ObjectNode objectNode = generator.generateSchema(objectParameter.genericType());
                    objectParameter.description().ifPresent(value -> objectNode.put("description", value));
                    properties.set(objectParameter.name(), objectNode);

                    if (objectParameter.required()) {
                        required.add(objectParameter.name());
                    }
                }));
    }

    public ObjectNode build(Class<?> type)
    {
        return build(Optional.empty(), type);
    }

    public ObjectNode build(Optional<String> description, Class<?> type)
    {
        ObjectNode objectNode = generator.generateSchema(type);
        description.ifPresent(value -> objectNode.put("description", value));
        addSchemaVersion(objectNode);
        return objectNode;
    }

    public static boolean isPrimitiveType(Type type)
    {
        if (type instanceof Class<?> rawType) {
            return primitiveTypes.contains(rawType);
        }
        return false;
    }

    public static boolean isSupportedType(Type type)
    {
        if (type instanceof Class<?> rawType) {
            return primitiveTypes.contains(rawType)
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
                && (parameterizedType.getActualTypeArguments()[1].equals(String.class) || parameterizedType.getActualTypeArguments()[1].equals(Object.class));
    }

    private ObjectNode buildObject(Optional<String> description, BiConsumer<ObjectNode, ArrayNode> propertiesConsumer)
    {
        ArrayNode requiredNode = jsonMapper.createArrayNode();
        ObjectNode propertiesNode = jsonMapper.createObjectNode();
        propertiesConsumer.accept(propertiesNode, requiredNode);

        ObjectNode objectNode = jsonMapper.createObjectNode();
        addSchemaVersion(objectNode);
        description.ifPresent(value -> objectNode.put("description", value));
        objectNode.put("type", "object");
        objectNode.set("properties", propertiesNode);
        objectNode.set("required", requiredNode);
        return objectNode;
    }

    private static void addSchemaVersion(ObjectNode objectNode)
    {
        objectNode.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    }
}

package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonSubType;
import io.airlift.json.ObjectMapperProvider;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.StructuredContentResult;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("NullOptional")
public class TestSerializationEdgeCases
{
    private static final Logger log = Logger.get(TestSerializationEdgeCases.class);

    private final ObjectMapper objectMapper;

    public TestSerializationEdgeCases()
    {
        JsonSubType jsonSubType = McpModule.buildJsonSubType();
        ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider()
                .withJsonSubTypes(ImmutableSet.of(jsonSubType));
        objectMapper = objectMapperProvider.get();
    }

    @Test
    public void testSerializationEdgeCases()
    {
        Map<Type, Object> cache = new HashMap<>();

        getClasses(JsonRpcRequest.class.getPackageName())
                .stream()
                .filter(Class::isRecord)
                .forEach(clazz -> computeAndTest(cache, clazz));
    }

    private Object build(Map<Type, Object> cache, Type type)
    {
        Class<?> rawType = rawType(type);

        if (Optional.class.isAssignableFrom(rawType)) {
            // Important edge case - many clients will omit fields that are optional
            // however, let's still validate that we can serialize/deserialize an instance with a value
            computeAndTest(cache, getComponentType(type));
            return null;
        }

        if (OptionalInt.class.isAssignableFrom(rawType) || OptionalLong.class.isAssignableFrom(rawType) || OptionalDouble.class.isAssignableFrom(rawType) || OptionalBoolean.class.isAssignableFrom(rawType)) {
            // Important edge case - many clients will omit fields that are optional
            return null;
        }

        if (Icon.Theme.class.isAssignableFrom(rawType)) {
            return Icon.Theme.DARK;
        }

        if (rawType.isEnum()) {
            return rawType.getEnumConstants()[0];
        }

        if (rawType.equals(ResourceContents.class)) {
            return new ResourceContents(null, "uri", "mime", null, Optional.of("content"));
        }

        if (StructuredContent.class.isAssignableFrom(rawType)) {
            return new StructuredContent<>("example");
        }

        if (StructuredContentResult.class.isAssignableFrom(rawType)) {
            return new StructuredContentResult<>(ImmutableList.of(), null, false);
        }

        if (JsonRpcRequest.class.isAssignableFrom(rawType)) {
            return new JsonRpcRequest<>(null, null, null, null);
        }

        if (JsonRpcResponse.class.isAssignableFrom(rawType)) {
            return new JsonRpcResponse<>(null, null, null, null);
        }

        if (CallToolRequest.class.isAssignableFrom(rawType) || GetPromptRequest.class.isAssignableFrom(rawType)) {
            // sometimes arguments are not provided for CallToolRequest/GetPromptRequest
            try {
                objectMapper.readerFor(rawType).readValue("{\"name\":\"example\"}");
            }
            catch (JsonProcessingException e) {
                fail(e);
            }
        }

        if (rawType.isRecord()) {
            RecordComponent[] recordComponents = rawType.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[recordComponents.length];

            Object[] args = new Object[recordComponents.length];
            for (int i = 0; i < recordComponents.length; i++) {
                parameterTypes[i] = recordComponents[i].getType();
                args[i] = computeAndTest(cache, recordComponents[i].getGenericType());
            }
            try {
                return rawType.getDeclaredConstructor(parameterTypes).newInstance(args);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (rawType.equals(String.class) || rawType.equals(Object.class)) {
            return "example";
        }

        if (rawType.equals(int.class) || rawType.equals(Integer.class)) {
            return 42;
        }

        if (rawType.equals(boolean.class) || rawType.equals(Boolean.class)) {
            return true;
        }

        if (List.class.isAssignableFrom(rawType)) {
            computeAndTest(cache, getComponentType(type));
            return ImmutableList.of();
        }

        if (Collection.class.isAssignableFrom(rawType)) {
            computeAndTest(cache, getComponentType(type));
            return ImmutableSet.of();
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return ImmutableMap.of();
        }

        if (ObjectNode.class.isAssignableFrom(rawType)) {
            ObjectNode typeNode = objectMapper.createObjectNode();
            typeNode.put("type", "string");
            return typeNode;
        }

        if (rawType.isSealed()) {
            // test all subclasses but return only the first one as the value
            return Stream.of(rawType.getPermittedSubclasses())
                    .map(subClass -> computeAndTest(cache, subClass))
                    .findFirst()
                    .orElseThrow();
        }

        throw new UnsupportedOperationException("Cannot build instance for class: " + rawType.getName());
    }

    private Type getComponentType(Type type)
    {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        throw new UnsupportedOperationException("Cannot determine component type for type: " + type.getTypeName());
    }

    private Class<?> rawType(Type type)
    {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return rawType(parameterizedType.getRawType());
        }
        throw new UnsupportedOperationException("Cannot determine raw type for type: " + type.getTypeName());
    }

    private Object computeAndTest(Map<Type, Object> cache, Type componentType)
    {
        if (cache.containsKey(componentType)) {
            return cache.get(componentType);
        }

        log.info("Checking " + componentType);

        Object value = build(cache, componentType);
        cache.put(componentType, value);

        try {
            String json = objectMapper.writeValueAsString(value);
            Object deserialized = objectMapper.readerFor(rawType(componentType)).readValue(json);

            if ((deserialized instanceof Optional<?> optional) && optional.isEmpty()) {
                assertThat(value).isNull();
            }
            else if ((deserialized instanceof OptionalInt optional) && optional.isEmpty()) {
                assertThat(value).isNull();
            }
            else if ((deserialized instanceof OptionalLong optional) && optional.isEmpty()) {
                assertThat(value).isNull();
            }
            else if ((deserialized instanceof OptionalDouble optional) && optional.isEmpty()) {
                assertThat(value).isNull();
            }
            else if ((deserialized instanceof OptionalBoolean optional) && optional == OptionalBoolean.UNDEFINED) {
                assertThat(value).isNull();
            }
            else {
                assertThat(deserialized).isEqualTo(value);
            }
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return value;
    }

    // from Claude.ai
    public static List<Class<?>> getClasses(String packageName)
    {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            List<Class<?>> classes = new ArrayList<>();

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.toURI());
                for (File file : requireNonNullElse(directory.listFiles(), new File[0])) {
                    if (file.getName().endsWith(".class")) {
                        String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                        classes.add(Class.forName(className));
                    }
                }
            }
            return classes;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

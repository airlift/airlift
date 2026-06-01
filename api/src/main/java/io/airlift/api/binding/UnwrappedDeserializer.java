package io.airlift.api.binding;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.airlift.api.ApiUnwrapped;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// this custom deserializer is needed because Jackson does not support deserializing unwrapped JSON
// see: https://github.com/FasterXML/jackson-databind/issues/3726
// NOTE: this deserializer relies on removing FAIL_ON_UNKNOWN_PROPERTIES from the mapper
class UnwrappedDeserializer
        extends JsonDeserializer<Object>
{
    private final Class<?> clazz;
    private final Constructor<?> constructor;

    UnwrappedDeserializer(Class<?> clazz)
    {
        this.clazz = clazz;

        // fail early check
        constructor = getDefaultRecordConstructor(clazz);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context)
            throws IOException
    {
        JsonNode tree = context.readTree(parser);

        RecordComponent[] recordComponents = clazz.getRecordComponents();
        Object[] arguments = new Object[recordComponents.length];

        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            JavaType javaType = context.constructType(recordComponent.getGenericType());

            Object value;
            if (recordComponent.isAnnotationPresent(ApiUnwrapped.class) && List.class.isAssignableFrom(recordComponent.getType())) {
                value = deserializeUnwrappedList(context, tree, recordComponent);
            }
            else if (recordComponent.isAnnotationPresent(ApiUnwrapped.class)) {
                value = context.readTreeAsValue(tree, javaType);
            }
            else {
                JsonNode componentNode = tree.get(recordComponent.getName());

                if (componentNode == null) {
                    if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                        value = Optional.empty();
                    }
                    else {
                        throw new JsonParseException(parser, "Expected component not found: %s".formatted(recordComponent.getName()));
                    }
                }
                else {
                    value = context.readTreeAsValue(componentNode, javaType);
                }
            }
            arguments[i] = value;
        }

        try {
            return constructor.newInstance(arguments);
        }
        catch (Exception e) {
            throw new JsonParseException(parser, "Could not create instance");
        }
    }

    private static Object deserializeUnwrappedList(DeserializationContext context, JsonNode tree, RecordComponent recordComponent)
            throws IOException
    {
        // @ApiUnwrapped List<T> where T is a record — each inner component is flattened as a parallel array.
        // Wire format: each inner component name maps to an array where element[i] corresponds to list[i].component
        Class<?> elementClass = getListElementClass(recordComponent);
        RecordComponent[] innerComponents = elementClass.getRecordComponents();

        // Determine list size from the first present array
        int size = 0;
        for (RecordComponent inner : innerComponents) {
            JsonNode arrayNode = tree.get(inner.getName());
            if (arrayNode != null && arrayNode.isArray()) {
                size = arrayNode.size();
                break;
            }
        }

        if (size == 0) {
            return List.of();
        }

        Constructor<?> elementConstructor = getDefaultRecordConstructor(elementClass);
        List<Object> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Object[] arguments = new Object[innerComponents.length];
            for (int j = 0; j < innerComponents.length; j++) {
                RecordComponent inner = innerComponents[j];
                JsonNode arrayNode = tree.get(inner.getName());
                JavaType innerType = context.constructType(inner.getGenericType());
                if (arrayNode != null && arrayNode.isArray() && i < arrayNode.size() && !arrayNode.get(i).isNull()) {
                    arguments[j] = context.readTreeAsValue(arrayNode.get(i), innerType);
                }
                else if (Optional.class.isAssignableFrom(inner.getType())) {
                    arguments[j] = Optional.empty();
                }
                else {
                    arguments[j] = null;
                }
            }
            try {
                result.add(elementConstructor.newInstance(arguments));
            }
            catch (Exception e) {
                throw new IOException("Could not create instance of " + elementClass, e);
            }
        }
        return result;
    }

    private static Class<?> getListElementClass(RecordComponent recordComponent)
    {
        java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) recordComponent.getGenericType();
        return (Class<?>) paramType.getActualTypeArguments()[0];
    }

    private static Constructor<?> getDefaultRecordConstructor(Class<?> clazz)
    {
        final Constructor<?> constructor;
        try {
            RecordComponent[] recordComponents = clazz.getRecordComponents();
            constructor = clazz.getConstructor(Stream.of(recordComponents).map(RecordComponent::getType).toArray(Class[]::new));
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not get record default constructor", e);
        }
        return constructor;
    }
}

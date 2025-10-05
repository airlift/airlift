package io.airlift.api.binding;

import io.airlift.api.ApiUnwrapped;
import io.airlift.log.Logger;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.stream.Stream;

// this custom deserializer is needed because Jackson does not support deserializing unwrapped JSON
// see: https://github.com/FasterXML/jackson-databind/issues/3726
// NOTE: this deserializer relies on removing FAIL_ON_UNKNOWN_PROPERTIES from the mapper
class UnwrappedDeserializer
        extends ValueDeserializer<Object>
{
    private static final Logger log = Logger.get(UnwrappedDeserializer.class);

    private final RecordComponent[] recordComponents;
    private final Constructor<?> constructor;

    UnwrappedDeserializer(Class<?> clazz)
    {
        // record components are immutable for a class; cache them to avoid the defensive array copy
        // that getRecordComponents() performs on every deserialize() call
        this.recordComponents = clazz.getRecordComponents();

        // fail early check
        constructor = getDefaultRecordConstructor(clazz, recordComponents);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context)
    {
        JsonNode tree = context.readTree(parser);

        Object[] arguments = new Object[recordComponents.length];

        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            JavaType javaType = context.constructType(recordComponent.getGenericType());

            Object value;
            if (recordComponent.isAnnotationPresent(ApiUnwrapped.class)) {
                value = context.readTreeAsValue(tree, javaType);
            }
            else {
                JsonNode componentNode = tree.get(recordComponent.getName());

                if (componentNode == null) {
                    if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                        value = Optional.empty();
                    }
                    else {
                        throw new StreamReadException(parser, "Expected component not found: %s".formatted(recordComponent.getName()));
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
            // do not expose the constructor failure (e.g. a validation message) to API clients
            log.debug(e, "Could not create instance of %s", constructor.getDeclaringClass().getName());
            throw new StreamReadException(parser, "Could not create instance");
        }
    }

    private static Constructor<?> getDefaultRecordConstructor(Class<?> clazz, RecordComponent[] recordComponents)
    {
        final Constructor<?> constructor;
        try {
            constructor = clazz.getConstructor(Stream.of(recordComponents).map(RecordComponent::getType).toArray(Class[]::new));
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not get record default constructor", e);
        }
        return constructor;
    }
}

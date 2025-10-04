package io.airlift.api.binding;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.api.ApiUnwrapped;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
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
        ObjectMapper objectMapper = (ObjectMapper) parser.getCodec();

        TreeNode tree = parser.readValueAsTree();

        RecordComponent[] recordComponents = clazz.getRecordComponents();
        Object[] arguments = new Object[recordComponents.length];

        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            JavaType javaType = objectMapper.getTypeFactory().constructType(recordComponent.getGenericType());

            Object value;
            if (recordComponent.isAnnotationPresent(ApiUnwrapped.class)) {
                value = objectMapper.treeToValue(tree, javaType);
            }
            else {
                TreeNode componentNode = tree.get(recordComponent.getName());

                if (componentNode == null) {
                    if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                        value = Optional.empty();
                    }
                    else {
                        throw new JsonParseException(parser, "Expected component not found: %s".formatted(recordComponent.getName()));
                    }
                }
                else {
                    value = objectMapper.treeToValue(componentNode, javaType);
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

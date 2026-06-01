package io.airlift.api.binding;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.airlift.api.ApiUnwrapped;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;

class UnwrappedSerializer
        extends JsonSerializer<Object>
{
    private final Class<?> clazz;

    UnwrappedSerializer(Class<?> clazz)
    {
        this.clazz = clazz;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Object> handledType()
    {
        return (Class<Object>) clazz;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException
    {
        gen.writeStartObject();

        RecordComponent[] recordComponents = clazz.getRecordComponents();
        for (RecordComponent recordComponent : recordComponents) {
            Object componentValue;
            try {
                componentValue = recordComponent.getAccessor().invoke(value);
            }
            catch (Exception e) {
                throw new IOException("Could not access component: " + recordComponent.getName(), e);
            }

            if (recordComponent.isAnnotationPresent(ApiUnwrapped.class) && List.class.isAssignableFrom(recordComponent.getType())) {
                serializeUnwrappedList(gen, serializers, recordComponent, componentValue);
            }
            else {
                gen.writeFieldName(recordComponent.getName());
                serializers.defaultSerializeValue(componentValue, gen);
            }
        }

        gen.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private static void serializeUnwrappedList(JsonGenerator gen, SerializerProvider serializers, RecordComponent recordComponent, Object componentValue)
            throws IOException
    {
        Class<?> elementClass = getListElementClass(recordComponent);
        RecordComponent[] innerComponents = elementClass.getRecordComponents();
        List<Object> list = (List<Object>) componentValue;

        for (RecordComponent innerComponent : innerComponents) {
            gen.writeFieldName(innerComponent.getName());
            gen.writeStartArray();

            if (list != null) {
                for (Object element : list) {
                    Object innerValue;
                    try {
                        innerValue = innerComponent.getAccessor().invoke(element);
                    }
                    catch (Exception e) {
                        throw new IOException("Could not access inner component: " + innerComponent.getName(), e);
                    }
                    serializers.defaultSerializeValue(innerValue, gen);
                }
            }

            gen.writeEndArray();
        }
    }

    private static Class<?> getListElementClass(RecordComponent recordComponent)
    {
        java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) recordComponent.getGenericType();
        return (Class<?>) paramType.getActualTypeArguments()[0];
    }

    static boolean hasUnwrappedList(Class<?> clazz)
    {
        return clazz.isRecord() && Stream.of(clazz.getRecordComponents())
                .anyMatch(rc -> rc.isAnnotationPresent(ApiUnwrapped.class) && List.class.isAssignableFrom(rc.getType()));
    }
}

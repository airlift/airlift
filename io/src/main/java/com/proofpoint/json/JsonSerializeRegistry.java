package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for serializers.
 */
public class JsonSerializeRegistry
{
    private static final Map<Class<?>, Class<? extends JsonSerializer<?>>>  serializerMap = new ConcurrentHashMap<Class<?>, Class<? extends JsonSerializer<?>>>();

    /**
     * Register a serializer for the given class
     *
     * @param clazz the class
     * @param serializer its serializer
     */
    public static<T> void          register(Class<T> clazz, Class<? extends JsonSerializer<T>> serializer)
    {
        serializerMap.put(clazz, serializer);
    }

    /**
     * Returns a serializer instance for the given class
     *
     * @param clazz the class (must already be registered via {@link #register(Class, Class)})
     * @return instance
     * @throws Exception instantiation problems or class isn't registered
     */
    static<T> JsonSerializer<T>      instanceFor(Class<?> clazz) throws Exception
    {
        Class<? extends JsonSerializer<?>>  serializerClazz = serializerMap.get(clazz);
        if ( serializerClazz == null )
        {
            throw new IllegalStateException("No serializer registered for: " + clazz.getName());
        }
        //noinspection unchecked
        return (JsonSerializer<T>)serializerClazz.newInstance();    // this cast is safe due to type-safety of register()
    }

    private JsonSerializeRegistry()
    {
    }

    public static class IntSerializer implements JsonSerializer<Integer>
    {
        @Override
        public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, Integer object) throws Exception
        {
            generator.writeNumber(object.intValue());
        }

        @Override
        public Integer readObject(JsonSerializeReader reader, JsonNode node) throws Exception
        {
            return node.getIntValue();
        }
    }

    public static class LongSerializer implements JsonSerializer<Long>
    {
        @Override
        public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, Long object) throws Exception
        {
            generator.writeNumber(object.longValue());
        }

        @Override
        public Long readObject(JsonSerializeReader reader, JsonNode node) throws Exception
        {
            return node.getLongValue();
        }
    }

    public static class DoubleSerializer implements JsonSerializer<Double>
    {
        @Override
        public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, Double object) throws Exception
        {
            generator.writeNumber(object);
        }

        @Override
        public Double readObject(JsonSerializeReader reader, JsonNode node) throws Exception
        {
            return node.getDoubleValue();
        }
    }

    public static class StringSerializer implements JsonSerializer<String>
    {
        @Override
        public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, String object) throws Exception
        {
            generator.writeString(object);
        }

        @Override
        public String readObject(JsonSerializeReader reader, JsonNode node) throws Exception
        {
            return node.getTextValue();
        }
    }

    static
    {
        register(Integer.class, IntSerializer.class);
        register(Double.class, DoubleSerializer.class);
        register(Long.class, LongSerializer.class);
        register(String.class, StringSerializer.class);
    }
}

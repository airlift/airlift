package com.proofpoint.json;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for serializers.
 */
public class JsonSerializeRegistry
{
    static final int        SERIALIZATION_VERSION = 1;
    static final int        DEFAULT_SERIALIZATION_VERSION = 1;

    static final String     OBJECT_VERSION_FIELD_NAME = "__object_version__";
    static final String     SERIALIZATION_VERSION_FIELD_NAME = "__file_version__";

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
}

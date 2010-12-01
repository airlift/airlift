package com.proofpoint.json;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * Abstraction for writing objects
 */
public class JsonSerializeWriter
{
    private final ByteArrayOutputStream   out;
    private final JsonGenerator           generator;

    public JsonSerializeWriter() throws IOException
    {
        out = new ByteArrayOutputStream();
        JsonFactory jsonFactory = new JsonFactory();
        generator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);

        generator.writeStartObject();
        //noinspection ConstantConditions
        if ( JsonSerializeRegistry.SERIALIZATION_VERSION != JsonSerializeRegistry.DEFAULT_SERIALIZATION_VERSION )
        {
            generator.writeNumberField(JsonSerializeRegistry.SERIALIZATION_VERSION_FIELD_NAME, JsonSerializeRegistry.SERIALIZATION_VERSION);
        }
    }

    /**
     * Write a top level object. The object must already be registered with a serializer
     *
     * @param obj the object to write
     * @return this (convenience for chaining)
     * @throws Exception errors
     */
    public<T> JsonSerializeWriter     writeObject(T obj) throws Exception
    {
        return writeObject(null, obj);
    }

    /**
     * Write an object. The object must already be registered with a serializer
     *
     * @param fieldName the name of the field being written or NULL for a top-level object
     * @param obj the object to write
     * @return this (convenience for chaining)
     * @throws Exception errors
     */
    public<T> JsonSerializeWriter     writeObject(String fieldName, T obj) throws Exception
    {
        if ( fieldName != null )
        {
            generator.writeObjectFieldStart(fieldName);
        }

        JsonSerializer<T> serializer = JsonSerializeRegistry.instanceFor(obj.getClass());
        if ( serializer.getVersion() != JsonSerializeRegistry.DEFAULT_SERIALIZATION_VERSION )
        {
            generator.writeNumberField(JsonSerializeRegistry.OBJECT_VERSION_FIELD_NAME, serializer.getVersion());
        }

        serializer.writeObject(this, generator, obj);

        if ( fieldName != null )
        {
            generator.writeEndObject();
        }

        return this;
    }

    /**
     * Writes a collection of objects
     *
     * @param fieldName the name of the field being written
     * @param c the collection
     * @return this (convenience for chaining)
     * @throws Exception errors
     */
    public<T> JsonSerializeWriter     writeObjectCollection(String fieldName, Collection<T> c) throws Exception
    {
        generator.writeArrayFieldStart(fieldName);
        for ( T obj : c )
        {
            generator.writeStartObject();
            writeObject(obj);
            generator.writeEndObject();
        }
        generator.writeEndArray();

        return this;
    }

    /**
     * Complete the serialization and return the serialized bytes
     *
     * @return bytes
     * @throws IOException errors
     */
    public byte[] close() throws IOException
    {
        generator.writeEndObject();
        generator.flush();

        return out.toByteArray();
    }
}

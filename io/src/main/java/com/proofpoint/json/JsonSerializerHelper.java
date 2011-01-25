package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;

/**
 * The Jackson provided classes for serialization are abstract class instead of interfaces and serialization
 * is separated from de-serialization. This interface is cleaner in that, a) it's an interface and b) it
 * locates serialization and de-serialization in the same class.
 */
public interface JsonSerializerHelper<T>
{
    /**
     * Called to serialize the object
     *
     * @param factory the factory in use
     * @param generator Jackson instance
     * @param object object to serialize
     * @throws Exception errors
     */
    public void writeObject(JsonUtilFactory factory, JsonGenerator generator, T object)
            throws Exception;

    /**
     * Called to de-serialize an object
     *
     * @param factory the factory in use
     * @param parser Jackson instance
     * @return object
     * @throws Exception errors
     */
    public T readObject(JsonUtilFactory factory, JsonParser parser)
            throws Exception;
}

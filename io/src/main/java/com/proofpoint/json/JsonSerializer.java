package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;

/**
 * Interface for classes that want to be serializable
 */
public interface JsonSerializer<T>
{
    /**
     * Called to write the object
     *
     * @param writer the current writer (you may recursively call {@link JsonSerializeWriter#writeObject(String, Object)}
     * @param generator the current generator
     * @param object the object instance to write
     * @throws Exception errors
     */
    public void      writeObject(JsonSerializeWriter writer, JsonGenerator generator, T object) throws Exception;

    /**
     * Called to read an object
     *
     * @param reader the current reader (you may recursively call {@link com.proofpoint.json.JsonSerializeReader#readObject(String, Class)}
     * @param node the JSON node for the object to read
     * @return the read object
     * @throws Exception errors
     */
    public T         readObject(JsonSerializeReader reader, JsonNode node) throws Exception;
}

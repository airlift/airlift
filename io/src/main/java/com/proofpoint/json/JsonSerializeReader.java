package com.proofpoint.json;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction for reading objects
 */
public class JsonSerializeReader
{
    private JsonNode            jsonNode;

    /**
     * @param data bytes as returned from {@link com.proofpoint.json.JsonSerializeWriter#close()}
     * @throws IOException errors
     */
    public JsonSerializeReader(byte[] data) throws IOException
    {
        ObjectMapper        mapper = new ObjectMapper();
        jsonNode = mapper.readTree(new ByteArrayInputStream(data));
    }

    /**
     * Read the next collection of objects. Caller is responsible for calling readObjectCollection() in the same order
     * as objects where written
     *
     * @param fieldName the name of the field being read
     * @param clazz the class of the object to read
     * @return the list
     * @throws Exception errors
     */
    public<T> List<T> readObjectCollection(String fieldName, Class<T> clazz) throws Exception
    {
        List<T>             list = new ArrayList<T>();

        JsonNode            nodeArray = jsonNode.get(fieldName);
        for ( JsonNode thisNode : nodeArray )
        {
            JsonNode savedJsonNode = jsonNode;
            jsonNode = thisNode;
            list.add(internalReadObject(clazz));
            jsonNode = savedJsonNode;
        }

        return list;
    }

    /**
     * Read a top level object.
     * 
     * @param clazz the class of the object to read
     * @return the object
     * @throws Exception errors
     */
    public<T> T     readObject(Class<T> clazz) throws Exception
    {
        return readObject(null, clazz);
    }

    /**
     * Read the next object. Caller is responsible for calling readObject() in the same order
     * as objects where written
     *
     * @param fieldName the name of the field being read or NULL for a top-level object
     * @param clazz the class of the object to read
     * @return the object
     * @throws Exception errors
     */
    public<T> T     readObject(String fieldName, Class<T> clazz) throws Exception
    {
        JsonNode        savedJsonNode = jsonNode;
        if ( fieldName != null )
        {
            jsonNode = jsonNode.get(fieldName);
        }

        T   object = internalReadObject(clazz);

        if ( fieldName != null )
        {
            jsonNode = savedJsonNode;
        }

        return object;
    }

    private <T> T internalReadObject(Class<T> clazz) throws Exception
    {
        JsonSerializer<T> serializer = JsonSerializeRegistry.instanceFor(clazz);
        return serializer.readObject(this, jsonNode);
    }
}

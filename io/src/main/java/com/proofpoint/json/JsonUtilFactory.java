package com.proofpoint.json;

import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.deser.CustomDeserializerFactory;
import org.codehaus.jackson.map.deser.StdDeserializerProvider;
import org.codehaus.jackson.map.ser.CustomSerializerFactory;
import org.codehaus.jackson.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Factory for de-coupled JSON serialization using Jackson. Provides methods
 * for keeping the serializer for an object separate from the object itself.
 */
public class JsonUtilFactory
{
    private static final Logger     log = Logger.get(JsonUtilFactory.class);

    private final CustomSerializerFactory customSerializerFactory;
    private final CustomDeserializerFactory customDeserializerFactory;

    /**
     * @param mappings set of custom mappings
     */
    @Inject
    public JsonUtilFactory(final Set<JsonSerializationMapping> mappings)
    {
        customSerializerFactory = new CustomSerializerFactory();
        customDeserializerFactory = new CustomDeserializerFactory();
        for ( JsonSerializationMapping mapping : mappings )
        {
            //noinspection unchecked
            customSerializerFactory.addSpecificMapping(mapping.getType(), wrapSerializer(mapping.getSerializer()));    // safe due to JsonSerializationMapping
            //noinspection unchecked
            customDeserializerFactory.addSpecificMapping(mapping.getType(), wrapDeserializer(mapping.getSerializer()));   // safe due to JsonSerializationMapping
        }
    }

    /**
     * Serialize the given object as a JSON object with one named field with the
     * value being the object. i.e.
     * <code><br>
     * {<br>
     *     "name" : {<i>value</i>}<br>
     * }<br>
     * </code>
     * @param name field name
     * @param value object to serialize
     * @return JSON object as byte array
     * @throws IOException errors
     */
    public<T> byte[] serializeWithName(String name, T value) throws IOException
    {
        ObjectMapper                mapper = new ObjectMapper();
        mapper.setSerializerFactory(customSerializerFactory);

        ObjectNode                  node = mapper.createObjectNode();
        node.putPOJO(name, value);

        ByteArrayOutputStream       out = new ByteArrayOutputStream();
        mapper.writeValue(out, node);
        return out.toByteArray();
    }

    /**
     * Serialize the given object as a JSON object i.e.
     * <code><br>
     * {<i>value</i>}
     * </code>
     * @param value object to serialize
     * @return JSON object as byte array
     * @throws IOException errors
     */
    public<T> byte[] serialize(T value) throws IOException
    {
        ObjectMapper                mapper = new ObjectMapper();
        mapper.setSerializerFactory(customSerializerFactory);
        ByteArrayOutputStream       out = new ByteArrayOutputStream();
        mapper.writeValue(out, value);
        return out.toByteArray();
    }

    /**
     * De-serialize an object. As JSON has no schema, you must pass in the type of
     * the object expected
     *
     * @param type object type
     * @param bytes serialized bytes
     * @return object
     * @throws IOException errors
     */
    public<T> T deserialize(Class<T> type, byte[] bytes) throws IOException
    {
        try
        {
            ObjectMapper                mapper = new ObjectMapper();
            mapper.setDeserializerProvider(new StdDeserializerProvider(customDeserializerFactory));
            return mapper.readValue(new ByteArrayInputStream(bytes), type);
        }
        catch ( IOException e )
        {
            log.error(e, "Could not deserialize type [%s] from JSON: %s", type.getName(), new String(bytes));
            throw e;
        }
    }

    /**
     * De-serialize a collection of objects. As JSON has no schema, you must pass in the type of
     * the object expected
     *
     * @param type object type
     * @param bytes serialized bytes
     * @return collection of objects
     * @throws IOException errors
     */
    public<T> Collection<T> deserializeCollection(Class<T> type, byte[] bytes) throws IOException
    {
        try
        {
            ObjectMapper                mapper = new ObjectMapper();
            mapper.setDeserializerProvider(new StdDeserializerProvider(customDeserializerFactory));
            JsonNode                    nodes = mapper.readTree(new ByteArrayInputStream(bytes));

            List<T>                     list = new ArrayList<T>();
            for ( JsonNode n : nodes )
            {
                list.add(deserializeContained(type, n));
            }
            return list;
        }
        catch ( IOException e )
        {
            log.error(e, "Could not deserialize collection of type [%s] from JSON: %s", type.getName(), new String(bytes));
            throw e;
        }
    }

    /**
     * Return an Object Mapper that contains the custom mappings registered in this factory
     *
     * @return new mapper
     */
    public ObjectMapper     newObjectMapper()
    {
        ObjectMapper                mapper = new ObjectMapper();
        mapper.setSerializerFactory(customSerializerFactory);
        mapper.setDeserializerProvider(new StdDeserializerProvider(customDeserializerFactory));
        return mapper;
    }

    /**
     * Convenience method for de-serializers. De-serialize the object at the given
     * node.
     *
     * @param type object type
     * @param node node for the object
     * @return the object
     * @throws IOException errors
     */
    public<T> T deserializeContained(Class<T> type, JsonNode node) throws IOException
    {
        return deserialize(type, node.toString().getBytes());
    }

    /**
     * Convenience method for de-serializers. De-serialize the colleciton of objects at the given
     * node.
     *
     * @param type object type
     * @param node node for the object
     * @return the object
     * @throws IOException errors
     */
    public<T> Collection<T> deserializeContainedCollection(Class<T> type, JsonNode node) throws IOException
    {
        return deserializeCollection(type, node.toString().getBytes());
    }

    private JsonDeserializer wrapDeserializer(final JsonSerializerHelper serializer)
    {
        return new JsonDeserializer()
        {
            @Override
            public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException
            {
                try
                {
                    return serializer.readObject(JsonUtilFactory.this, parser);
                }
                catch ( IOException e )
                {
                    throw e;
                }
                catch ( Exception e )
                {
                    throw new IOException(e);
                }
            }
        };
    }

    private JsonSerializer wrapSerializer(final JsonSerializerHelper serializer)
    {
        return new JsonSerializer()
        {
            @Override
            public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException
            {
                try
                {
                    //noinspection unchecked
                    serializer.writeObject(JsonUtilFactory.this, generator, value);   // is type safe due to JsonSerializationMapping
                }
                catch ( IOException e )
                {
                    throw e;
                }
                catch ( Exception e )
                {
                    throw new IOException(e);
                }
            }
        };
    }
}

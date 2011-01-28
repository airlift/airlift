package com.proofpoint.experimental.json;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;
import static org.codehaus.jackson.map.SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS;

public class JsonCodec<T>
{
    public static <T> JsonCodec<T> createJsonCodec(Class<T> type)
    {
        Preconditions.checkNotNull(type, "type");
        return new JsonCodec<T>(type);
    }

    private final ObjectMapper mapper;
    private final JavaType javaType;

    protected JsonCodec()
    {
        // Courtesy of Neal Gafter via Jackson
        // sanity check, should never happen
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof Class<?>) {
            throw new IllegalArgumentException("Internal error: JsonCodec constructed without actual type information");
        }

        Type type = ((ParameterizedType) superClass).getActualTypeArguments()[0];

        mapper = new ObjectMapper();
        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.getSerializationConfig().disable(WRITE_DATES_AS_TIMESTAMPS);

        javaType = TypeFactory.type(type);
    }

    private JsonCodec(Type type)
    {
        mapper = new ObjectMapper();
        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.getSerializationConfig().disable(WRITE_DATES_AS_TIMESTAMPS);

        javaType = TypeFactory.type(type);
    }

    public JsonCodec<T> setPrettyPrint(boolean prettyPrint)
    {
        if (prettyPrint) {
            mapper.getSerializationConfig().enable(INDENT_OUTPUT);
        }
        else {
            mapper.getSerializationConfig().disable(INDENT_OUTPUT);
        }
        return this;
    }

    /**
     * @return Parsed response; never null
     */
    public T fromJson(String json)
            throws IllegalArgumentException
    {
        try {
            return (T) mapper.readValue(json, javaType);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("Invalid %s json string", javaType.getRawClass().getSimpleName()), e);
        }
    }

    public String toJson(T farmdResponse)
            throws IllegalArgumentException
    {
        try {
            return mapper.writeValueAsString(farmdResponse);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("%s could not be converted to json", javaType.getRawClass().getSimpleName()), e);
        }
    }
}

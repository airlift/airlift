/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import io.airlift.json.LengthLimitedWriter.LengthLimitExceededException;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class JsonCodec<T>
{
    private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(new Supplier<ObjectMapper>()
    {
        public ObjectMapper get()
        {
            return new ObjectMapperProvider().get().enable(INDENT_OUTPUT);
        }
    });

    public static <T> JsonCodec<T> jsonCodec(Class<T> type)
    {
        checkNotNull(type, "type is null");

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), type);
    }

    public static <T> JsonCodec<T> jsonCodec(TypeToken<T> type)
    {
        checkNotNull(type, "type is null");

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), type.getType());
    }

    public static <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        checkNotNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type)
                .getType();

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), listType);
    }

    public static <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        checkNotNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type.getTypeToken())
                .getType();

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), listType);
    }

    public static <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        checkNotNull(keyType, "keyType is null");
        checkNotNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType)
                .getType();

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), mapType);
    }

    public static <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        checkNotNull(keyType, "keyType is null");
        checkNotNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType.getTypeToken())
                .getType();

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), mapType);
    }

    private final ObjectMapper mapper;
    private final Type type;
    private final JavaType javaType;

    JsonCodec(ObjectMapper mapper, Type type)
    {
        this.mapper = mapper;
        this.type = type;
        this.javaType = mapper.getTypeFactory().constructType(type);
    }

    /**
     * Gets the type this codec supports.
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Converts the specified json string into an instance of type T.
     *
     * @param json the json string to parse
     * @return parsed response; never null
     * @throws IllegalArgumentException if the json string can not be converted to the type T
     */
    public T fromJson(String json)
            throws IllegalArgumentException
    {
        try {
            return mapper.readValue(json, javaType);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(format("Invalid JSON string for %s", javaType), e);
        }
    }

    /**
     * Converts the specified instance to json.
     *
     * @param instance the instance to convert to json
     * @return json string
     * @throws IllegalArgumentException if the specified instance can not be converted to json
     */
    public String toJson(T instance)
            throws IllegalArgumentException
    {
        try {
            return mapper.writeValueAsString(instance);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(format("%s could not be converted to JSON", instance.getClass().getName()), e);
        }
    }

    /**
     * Converts the specified instance to optional json string with a length limit. Returns Optional.empty() if length limit is exceeded.
     *
     * @param instance the instance to convert to json
     * @param lengthLimit the maximum length of the serialized string in characters
     * @return json string
     * @throws IllegalArgumentException if the specified instance can not be converted to json
     */
    public Optional<String> toJsonWithLengthLimit(T instance, int lengthLimit)
    {
        try (StringWriter stringWriter = new StringWriter();
                LengthLimitedWriter lengthLimitedWriter = new LengthLimitedWriter(stringWriter, lengthLimit)) {
            mapper.writeValue(lengthLimitedWriter, instance);
            return Optional.of(stringWriter.getBuffer().toString());
        }
        catch (LengthLimitExceededException e) {
            return Optional.empty();
        }
        catch (IOException e) {
            throw new IllegalArgumentException(format("%s could not be converted to JSON", instance.getClass().getName()), e);
        }
    }

    /**
     * Coverts the specified json bytes (UTF-8) into an instance of type T.
     *
     * @param json the json bytes (UTF-8) to parse
     * @return parsed response; never null
     * @throws IllegalArgumentException if the json bytes can not be converted to the type T
     */
    public T fromJson(byte[] json)
            throws IllegalArgumentException
    {
        try {
            return mapper.readValue(json, javaType);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(format("Invalid JSON bytes for %s", javaType), e);
        }
    }

    /**
     * Converts the specified instance to json.
     *
     * @param instance the instance to convert to json
     * @return json bytes (UTF-8)
     * @throws IllegalArgumentException if the specified instance can not be converted to json
     */
    public byte[] toJsonBytes(T instance)
            throws IllegalArgumentException
    {
        try {
            return mapper.writeValueAsBytes(instance);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(format("%s could not be converted to JSON", instance.getClass().getName()), e);
        }
    }

    @SuppressWarnings("unchecked")
    TypeToken<T> getTypeToken()
    {
        return (TypeToken<T>) TypeToken.of(type);
    }
}

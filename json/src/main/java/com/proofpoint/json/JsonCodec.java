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
package com.proofpoint.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.google.common.base.Preconditions.checkNotNull;

public class JsonCodec<T>
{
    private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(new Supplier<ObjectMapper>()
    {
        @Override
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

    public static <T> JsonCodec<T> jsonCodec(TypeLiteral<T> type)
    {
        checkNotNull(type, "type is null");

        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), type.getType());
    }

    public static <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type);
        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), listType);
    }

    public static <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type.getType());
        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), listType);
    }

    public static <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        checkNotNull(keyType, "keyType is null");
        checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        return new JsonCodec<>(OBJECT_MAPPER_SUPPLIER.get(), mapType);
    }

    public static <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        checkNotNull(keyType, "keyType is null");
        checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType.getType());
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
     * Coverts the specified json string into an instance of type T.
     *
     * @return Parsed response; never null
     * @throws IllegalArgumentException if the json string can not be converted to the type T
     */
    @SuppressWarnings("unchecked")
    public T fromJson(String json)
            throws IllegalArgumentException
    {
        try {
            return (T) mapper.readValue(json, javaType);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("Invalid %s json string", javaType), e);
        }
    }

    /**
     * Converts the specified instance to json.
     *
     * @param instance the instance to convert to json
     * @return Parsed response; never null
     * @throws IllegalArgumentException if the specified instance can not be converted to json
     */
    public String toJson(T instance)
            throws IllegalArgumentException
    {
        try {
            return mapper.writeValueAsString(instance);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("%s could not be converted to json", instance.getClass().getName()), e);
        }
    }
}

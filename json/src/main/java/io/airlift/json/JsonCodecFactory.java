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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;
import org.codehaus.jackson.map.ObjectMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;

public class JsonCodecFactory
{
    private final Provider<ObjectMapper> objectMapperProvider;
    private final boolean prettyPrint;

    public JsonCodecFactory()
    {
        this(new ObjectMapperProvider());
    }

    @Inject
    public JsonCodecFactory(Provider<ObjectMapper> objectMapperProvider)
    {
        this(objectMapperProvider, false);
    }

    public JsonCodecFactory(Provider<ObjectMapper> objectMapperProvider, boolean prettyPrint)
    {
        this.objectMapperProvider = objectMapperProvider;
        this.prettyPrint = prettyPrint;
    }

    public JsonCodecFactory prettyPrint()
    {
        return new JsonCodecFactory(objectMapperProvider, true);
    }

    public <T> JsonCodec<T> jsonCodec(Class<T> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        return new JsonCodec<T>(createObjectMapper(), type);
    }

    public <T> JsonCodec<T> jsonCodec(Type type)
    {
        Preconditions.checkNotNull(type, "type is null");

        return new JsonCodec<T>(createObjectMapper(), type);
    }

    public <T> JsonCodec<T> jsonCodec(TypeLiteral<T> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        return new JsonCodec<T>(createObjectMapper(), type.getType());
    }

    public <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type);
        return new JsonCodec<List<T>>(createObjectMapper(), listType);
    }

    public <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type.getType());
        return new JsonCodec<List<T>>(createObjectMapper(), listType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        Preconditions.checkNotNull(keyType, "keyType is null");
        Preconditions.checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        return new JsonCodec<Map<K, V>>(createObjectMapper(), mapType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        Preconditions.checkNotNull(keyType, "keyType is null");
        Preconditions.checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType.getType());
        return new JsonCodec<Map<K, V>>(createObjectMapper(), mapType);
    }

    private ObjectMapper createObjectMapper()
    {
        ObjectMapper objectMapper = null;

        // Work around for http://code.google.com/p/google-guice/issues/detail?id=627
        RuntimeException lastException = null;
        for (int i = 0; objectMapper == null && i< 10; i++) {
            try {
                objectMapper = objectMapperProvider.get();
            }
            catch (RuntimeException e) {
                lastException = e;
            }
        }
        if (objectMapper == null) {
            throw lastException;
        }

        if (prettyPrint) {
            objectMapper.getSerializationConfig().enable(INDENT_OUTPUT);
        }
        else {
            objectMapper.getSerializationConfig().disable(INDENT_OUTPUT);
        }
        return objectMapper;
    }
}

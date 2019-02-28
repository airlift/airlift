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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;

import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public class JsonCodecFactory
        extends CodecFactory
{
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
        super(objectMapperProvider, (objectMapper, type) -> new JsonCodec(objectMapper, type));
        this.prettyPrint = prettyPrint;
    }

    public JsonCodecFactory prettyPrint()
    {
        return new JsonCodecFactory(objectMapperProvider, true);
    }

    @Deprecated
    public <T> JsonCodec<T> jsonCodec(Class<T> type)
    {
        return (JsonCodec<T>) codec(type);
    }

    @Deprecated
    public <T> JsonCodec<T> jsonCodec(Type type)
    {
        return (JsonCodec<T>) codec(type);
    }

    @Deprecated
    public <T> JsonCodec<T> jsonCodec(TypeToken<T> type)
    {
        return (JsonCodec<T>) codec(type.getType());
    }

    @Deprecated
    public <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        return (JsonCodec<List<T>>) listCodec(type);
    }

    @Deprecated
    public <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        return (JsonCodec<List<T>>) listCodec(type);
    }

    @Deprecated
    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        return (JsonCodec<Map<K, V>>) mapCodec(keyType, valueType);
    }

    @Deprecated
    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        return (JsonCodec<Map<K, V>>) mapCodec(keyType, valueType);
    }

    @Override
    ObjectMapper createObjectMapper()
    {
        return objectMapperProvider.get().configure(INDENT_OUTPUT, prettyPrint);
    }
}

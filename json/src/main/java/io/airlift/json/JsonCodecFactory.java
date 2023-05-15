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
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Objects.requireNonNull;

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
        requireNonNull(type, "type is null");

        return new JsonCodec<>(createObjectMapper(), type);
    }

    public <T> JsonCodec<T> jsonCodec(Type type)
    {
        requireNonNull(type, "type is null");

        return new JsonCodec<>(createObjectMapper(), type);
    }

    public <T> JsonCodec<T> jsonCodec(TypeToken<T> type)
    {
        requireNonNull(type, "type is null");

        return new JsonCodec<>(createObjectMapper(), type.getType());
    }

    public <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type)
                .getType();

        return new JsonCodec<>(createObjectMapper(), listType);
    }

    public <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type.getTypeToken())
                .getType();

        return new JsonCodec<>(createObjectMapper(), listType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType)
                .getType();

        return new JsonCodec<>(createObjectMapper(), mapType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType.getTypeToken())
                .getType();

        return new JsonCodec<>(createObjectMapper(), mapType);
    }

    private ObjectMapper createObjectMapper()
    {
        return objectMapperProvider.get().configure(INDENT_OUTPUT, prettyPrint);
    }
}

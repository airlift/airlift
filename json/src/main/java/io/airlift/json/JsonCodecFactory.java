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

import com.fasterxml.jackson.databind.json.JsonMapper;
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
    private final JsonMapper jsonMapper;

    public JsonCodecFactory()
    {
        this(new JsonMapperProvider().get());
    }

    @Inject
    public JsonCodecFactory(JsonMapper jsonMapper)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @Deprecated
    public JsonCodecFactory(Provider<JsonMapper> jsonMapperProvider)
    {
        this(jsonMapperProvider.get());
    }

    @Deprecated
    public JsonCodecFactory(Provider<JsonMapper> jsonMapperProvider, boolean prettyPrint)
    {
        this(withPrettyPrint(jsonMapperProvider.get(), prettyPrint));
    }

    public JsonCodecFactory prettyPrint()
    {
        return new JsonCodecFactory(withPrettyPrint(jsonMapper, true));
    }

    public <T> JsonCodec<T> jsonCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        return new JsonCodec<>(jsonMapper, type);
    }

    public <T> JsonCodec<T> jsonCodec(Type type)
    {
        requireNonNull(type, "type is null");

        return new JsonCodec<>(jsonMapper, type);
    }

    public <T> JsonCodec<T> jsonCodec(TypeToken<T> type)
    {
        requireNonNull(type, "type is null");

        return new JsonCodec<>(jsonMapper, type.getType());
    }

    public <T> JsonCodec<List<T>> listJsonCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type)
                .getType();

        return new JsonCodec<>(jsonMapper, listType);
    }

    public <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type.getTypeToken())
                .getType();

        return new JsonCodec<>(jsonMapper, listType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType)
                .getType();

        return new JsonCodec<>(jsonMapper, mapType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType.getTypeToken())
                .getType();

        return new JsonCodec<>(jsonMapper, mapType);
    }

    private static JsonMapper withPrettyPrint(JsonMapper mapper, boolean prettyPrint)
    {
        return mapper
                .rebuild()
                .configure(INDENT_OUTPUT, prettyPrint)
                .build();
    }
}

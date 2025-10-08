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

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static tools.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public class JsonCodecFactory
{
    private final JsonMapper jsonMapper;

    public JsonCodecFactory()
    {
        this(new JsonMapperProvider());
    }

    @Inject
    public JsonCodecFactory(Provider<JsonMapper> jsonMapperProvider)
    {
        this(jsonMapperProvider, false);
    }

    public JsonCodecFactory(Provider<JsonMapper> jsonMapperProvider, boolean prettyPrint)
    {
        this.jsonMapper = createJsonMapper(requireNonNull(jsonMapperProvider, "jsonMapperProvider is null"), prettyPrint);
    }

    public JsonCodecFactory prettyPrint()
    {
        return new JsonCodecFactory(() -> jsonMapper, true);
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
                .where(new TypeParameter<T>() {}, type)
                .getType();

        return new JsonCodec<>(jsonMapper, listType);
    }

    public <T> JsonCodec<List<T>> listJsonCodec(JsonCodec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type.getTypeToken())
                .getType();

        return new JsonCodec<>(jsonMapper, listType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType)
                .getType();

        return new JsonCodec<>(jsonMapper, mapType);
    }

    public <K, V> JsonCodec<Map<K, V>> mapJsonCodec(Class<K> keyType, JsonCodec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType.getTypeToken())
                .getType();

        return new JsonCodec<>(jsonMapper, mapType);
    }

    private static JsonMapper createJsonMapper(Provider<JsonMapper> jsonMapperProvider, boolean prettyPrint)
    {
        if (prettyPrint) {
            return jsonMapperProvider.get().rebuild()
                    .enable(INDENT_OUTPUT)
                    .build();
        }
        return jsonMapperProvider.get();
    }
}

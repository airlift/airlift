/*
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

import javax.inject.Provider;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

public abstract class CodecFactory
{
    final Provider<ObjectMapper> objectMapperProvider;
    final BiFunction<ObjectMapper, Type, Codec> codecConstructor;

    public CodecFactory(Provider<ObjectMapper> objectMapperProvider, BiFunction<ObjectMapper, Type, Codec> codecConstructor)
    {
        this.objectMapperProvider = requireNonNull(objectMapperProvider, "objectMapperProvider is null");
        this.codecConstructor = requireNonNull(codecConstructor, "codecConstructor is null");
    }

    public <T> Codec<T> codec(Type type)
    {
        requireNonNull(type, "type is null");
        return codecConstructor.apply(createObjectMapper(), type);
    }

    public <T> Codec<List<T>> listCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, type)
                .getType();

        return codecConstructor.apply(createObjectMapper(), listType);
    }

    public <T> Codec<List<T>> listCodec(Codec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {}, (TypeToken<T>) TypeToken.of(type.getType()))
                .getType();

        return codecConstructor.apply(createObjectMapper(), listType);
    }

    public <K, V> Codec<Map<K, V>> mapCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType)
                .getType();

        return codecConstructor.apply(createObjectMapper(), mapType);
    }

    public <K, V> Codec<Map<K, V>> mapCodec(Class<K> keyType, Codec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, (TypeToken) TypeToken.of(valueType.getType()))
                .getType();

        return codecConstructor.apply(createObjectMapper(), mapType);
    }

    abstract ObjectMapper createObjectMapper();
}

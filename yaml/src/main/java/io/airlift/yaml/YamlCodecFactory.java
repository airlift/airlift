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
package io.airlift.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class YamlCodecFactory
{
    private final ObjectMapper yamlMapper;

    public YamlCodecFactory()
    {
        this(new YamlMapperProvider().get());
    }

    @Inject
    public YamlCodecFactory(YAMLMapper yamlMapper)
    {
        this((ObjectMapper) yamlMapper);
    }

    private YamlCodecFactory(ObjectMapper yamlMapper)
    {
        this.yamlMapper = requireNonNull(yamlMapper, "yamlMapper is null");
    }

    /**
     * No-op for symmetry with JsonCodecFactory. YAML output is block-formatted regardless of
     * SerializationFeature.INDENT_OUTPUT, so there is no practical difference between a default
     * and a "pretty" YAML codec.
     */
    public YamlCodecFactory prettyPrint()
    {
        return this;
    }

    public <T> YamlCodec<T> yamlCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        return new YamlCodec<>(yamlMapper, type);
    }

    public <T> YamlCodec<T> yamlCodec(Type type)
    {
        requireNonNull(type, "type is null");

        return new YamlCodec<>(yamlMapper, type);
    }

    public <T> YamlCodec<T> yamlCodec(TypeToken<T> type)
    {
        requireNonNull(type, "type is null");

        return new YamlCodec<>(yamlMapper, type.getType());
    }

    public <T> YamlCodec<List<T>> listYamlCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type)
                .getType();

        return new YamlCodec<>(yamlMapper, listType);
    }

    public <T> YamlCodec<List<T>> listYamlCodec(YamlCodec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type.getTypeToken())
                .getType();

        return new YamlCodec<>(yamlMapper, listType);
    }

    public <K, V> YamlCodec<Map<K, V>> mapYamlCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType)
                .getType();

        return new YamlCodec<>(yamlMapper, mapType);
    }

    public <K, V> YamlCodec<Map<K, V>> mapYamlCodec(Class<K> keyType, YamlCodec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType.getTypeToken())
                .getType();

        return new YamlCodec<>(yamlMapper, mapType);
    }
}

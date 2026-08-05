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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.base.Suppliers;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.ThreadSafe;
import io.airlift.jackson.LengthLimitedWriter;
import io.airlift.jackson.LengthLimitedWriter.LengthLimitExceededException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

@ThreadSafe
public class YamlCodec<T>
{
    private static final ObjectMapper YAML_MAPPER = new YamlMapperProvider().get();

    public static <T> YamlCodec<T> yamlCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        return new YamlCodec<>(YAML_MAPPER, type);
    }

    public static <T> YamlCodec<T> yamlCodec(TypeToken<T> type)
    {
        requireNonNull(type, "type is null");

        return new YamlCodec<>(YAML_MAPPER, type.getType());
    }

    public static <T> YamlCodec<List<T>> listYamlCodec(Class<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type)
                .getType();

        return new YamlCodec<>(YAML_MAPPER, listType);
    }

    public static <T> YamlCodec<List<T>> listYamlCodec(YamlCodec<T> type)
    {
        requireNonNull(type, "type is null");

        Type listType = new TypeToken<List<T>>() {}
                .where(new TypeParameter<>() {}, type.getTypeToken())
                .getType();

        return new YamlCodec<>(YAML_MAPPER, listType);
    }

    public static <K, V> YamlCodec<Map<K, V>> mapYamlCodec(Class<K> keyType, Class<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType)
                .getType();

        return new YamlCodec<>(YAML_MAPPER, mapType);
    }

    public static <K, V> YamlCodec<Map<K, V>> mapYamlCodec(Class<K> keyType, YamlCodec<V> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        Type mapType = new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<>() {}, keyType)
                .where(new TypeParameter<>() {}, valueType.getTypeToken())
                .getType();

        return new YamlCodec<>(YAML_MAPPER, mapType);
    }

    private final TypeToken<T> typeToken;
    private final Type type;
    private final Supplier<ObjectWriter> writer;
    private final Supplier<ObjectReader> reader;

    YamlCodec(ObjectMapper mapper, Type type)
    {
        JavaType javaType = mapper.constructType(type);
        this.typeToken = (TypeToken<T>) TypeToken.of(type);
        this.type = javaType;
        this.writer = Suppliers.memoize(() -> mapper.writerFor(javaType));
        this.reader = Suppliers.memoize(() -> mapper.readerFor(javaType));
    }

    public Type getType()
    {
        return type;
    }

    public T fromYaml(String yaml)
            throws IllegalArgumentException
    {
        try {
            return reader.get().readValue(yaml);
        }
        catch (Exception e) {
            throw mapException(e, "string", type);
        }
    }

    public String toYaml(T instance)
            throws IllegalArgumentException
    {
        try {
            return writer.get().writeValueAsString(instance);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("%s could not be converted to YAML".formatted(instance.getClass().getName()), e);
        }
    }

    public Optional<String> toYamlWithLengthLimit(T instance, int lengthLimit)
    {
        try (StringWriter stringWriter = new StringWriter();
                LengthLimitedWriter lengthLimitedWriter = new LengthLimitedWriter(stringWriter, lengthLimit)) {
            writer.get().writeValue(lengthLimitedWriter, instance);
            return Optional.of(stringWriter.getBuffer().toString());
        }
        catch (LengthLimitExceededException e) {
            return Optional.empty();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("%s could not be converted to YAML".formatted(instance.getClass().getName()), e);
        }
    }

    public T fromYaml(byte[] yaml)
            throws IllegalArgumentException
    {
        try {
            return reader.get().readValue(yaml);
        }
        catch (Exception e) {
            throw mapException(e, "bytes", type);
        }
    }

    public byte[] toYamlBytes(T instance)
            throws IllegalArgumentException
    {
        try {
            return writer.get().writeValueAsBytes(instance);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("%s could not be converted to YAML".formatted(instance.getClass().getName()), e);
        }
    }

    public T fromYaml(InputStream yaml)
            throws IllegalArgumentException
    {
        try {
            return reader.get().readValue(yaml);
        }
        catch (Exception e) {
            throw mapException(e, "stream", type);
        }
    }

    public T fromYaml(Reader yaml)
            throws IllegalArgumentException
    {
        try {
            return reader.get().readValue(yaml);
        }
        catch (Exception e) {
            throw mapException(e, "characters", type);
        }
    }

    private static IllegalArgumentException mapException(Exception e, String source, Type type)
    {
        return switch (e) {
            case MismatchedInputException mismatchedInputException when mismatchedInputException.getMessage().contains("not allowed as per `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`") -> new IllegalArgumentException("Found characters after the expected end of input", e);
            case IllegalArgumentException iae -> iae;
            default -> new IllegalArgumentException("Invalid YAML %s for %s".formatted(source, type), e);
        };
    }

    @SuppressWarnings("unchecked")
    TypeToken<T> getTypeToken()
    {
        return typeToken;
    }
}

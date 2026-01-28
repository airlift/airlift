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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.Version;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.datatype.guava.GuavaModule;
import tools.jackson.datatype.joda.JodaModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class ObjectMapperProvider
        implements Provider<JsonMapper>
{
    private final JsonFactory jsonFactory;
    private final List<Consumer<JsonMapper.Builder>> customizers = new ArrayList<>();

    private Map<Class<?>, ValueSerializer<?>> keySerializers;
    private Map<Class<?>, KeyDeserializer> keyDeserializers;
    private Map<Class<?>, ValueSerializer<?>> jsonSerializers;
    private Map<Class<?>, ValueDeserializer<?>> jsonDeserializers;

    private final Set<JsonSubType> jsonSubTypes = new HashSet<>();
    private final Set<JacksonModule> modules = new HashSet<>();
    private boolean prettyPrint;

    @Inject
    public ObjectMapperProvider()
    {
        this(new JsonFactoryBuilder());
    }

    public ObjectMapperProvider(JsonFactory jsonFactory)
    {
        this(new JsonFactoryBuilder(requireNonNull(jsonFactory, "jsonFactory is null")));
    }

    private ObjectMapperProvider(JsonFactoryBuilder jsonFactoryBuilder)
    {
        // Disable the length limit, caller will be responsible for validating the input length
        jsonFactoryBuilder.streamReadConstraints(StreamReadConstraints
                .builder()
                .maxStringLength(Integer.MAX_VALUE)
                .maxNestingDepth(Integer.MAX_VALUE)
                .maxNameLength(Integer.MAX_VALUE)
                .maxDocumentLength(Long.MAX_VALUE)
                .build());

        jsonFactoryBuilder.streamWriteConstraints(StreamWriteConstraints
                .builder()
                .maxNestingDepth(Integer.MAX_VALUE)
                .build());

        jsonFactoryBuilder.enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER);
        jsonFactoryBuilder.enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
        jsonFactoryBuilder.enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER);

        jsonFactoryBuilder.recyclerPool(JsonRecyclerPools.threadLocalPool());

        jsonFactory = jsonFactoryBuilder.build();

        modules.add(new GuavaModule());
        modules.add(new RecordAutoDetectModule());

        try {
            getClass().getClassLoader().loadClass("org.joda.time.DateTime");
            modules.add(new JodaModule());
        }
        catch (ClassNotFoundException ignored) {
        }
    }

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, ValueSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers = ImmutableMap.copyOf(jsonSerializers);
    }

    public ObjectMapperProvider withJsonSerializers(Map<Class<?>, ValueSerializer<?>> jsonSerializers)
    {
        setJsonSerializers(jsonSerializers);
        return this;
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, ValueDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers = ImmutableMap.copyOf(jsonDeserializers);
    }

    public ObjectMapperProvider withJsonDeserializers(Map<Class<?>, ValueDeserializer<?>> jsonDeserializers)
    {
        setJsonDeserializers(jsonDeserializers);
        return this;
    }

    @Inject(optional = true)
    public void setKeySerializers(@JsonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        this.keySerializers = keySerializers;
    }

    public ObjectMapperProvider withKeySerializers(@JsonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        setKeySerializers(keySerializers);
        return this;
    }

    @Inject(optional = true)
    public void setKeyDeserializers(@JsonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        this.keyDeserializers = keyDeserializers;
    }

    public ObjectMapperProvider withKeyDeserializers(@JsonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        setKeyDeserializers(keyDeserializers);
        return this;
    }

    @Inject(optional = true)
    public void setModules(Set<JacksonModule> modules)
    {
        this.modules.addAll(modules);
    }

    public ObjectMapperProvider withModules(Set<JacksonModule> modules)
    {
        setModules(modules);
        return this;
    }

    @Inject(optional = true)
    public void setJsonSubTypes(Set<JsonSubType> jsonSubTypes)
    {
        this.jsonSubTypes.addAll(jsonSubTypes);
    }

    public ObjectMapperProvider withJsonSubTypes(Set<JsonSubType> jsonSubTypes)
    {
        setJsonSubTypes(jsonSubTypes);
        return this;
    }

    public ObjectMapperProvider withPrettyPrint(boolean prettyPrint)
    {
        this.prettyPrint = prettyPrint;
        return this;
    }

    public ObjectMapperProvider withCustomizer(Consumer<JsonMapper.Builder> customizer)
    {
        this.customizers.add(customizer);
        return this;
    }

    @Override
    public JsonMapper get()
    {
        JsonMapper.Builder jsonMapper = JsonMapper.builder(jsonFactory);

        // ignore unknown fields (for backwards compatibility)
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        if (prettyPrint) {
            jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        // use ISO dates
        jsonMapper.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);

        // do not allow converting a float to an integer
        jsonMapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // When serialization fails in the middle, it's better to return a truncated (invalid) JSON
        // than something that could be interpreted as a valid (but incorrect) result.
        // This is especially applicable to server endpoints that return JSON responses.
        jsonMapper.disable(StreamWriteFeature.AUTO_CLOSE_CONTENT);

        // Skip fields that are null or absent (Optional) when serializing objects.
        // This only applies to mapped object fields, not containers like Map or List.
        jsonMapper.changeDefaultPropertyInclusion(_ -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS));

        // When a field is null or absent (Optional) in JSON, set the value to a default value (i.e. empty Optional)
        jsonMapper.changeDefaultNullHandling(_ -> JsonSetter.Value.construct(Nulls.SET, Nulls.SET));

        // disable auto detection of json properties... all properties must be explicit
        jsonMapper.changeDefaultVisibility(visibilityChecker -> visibilityChecker
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
                .withScalarConstructorVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));

        jsonMapper.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
        jsonMapper.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        jsonMapper.disable(MapperFeature.INFER_PROPERTY_MUTATORS);
        jsonMapper.disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS);

        if (jsonSerializers != null || jsonDeserializers != null || keySerializers != null || keyDeserializers != null) {
            SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null, null, null));
            if (jsonSerializers != null) {
                for (Entry<Class<?>, ValueSerializer<?>> entry : jsonSerializers.entrySet()) {
                    addSerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (jsonDeserializers != null) {
                for (Entry<Class<?>, ValueDeserializer<?>> entry : jsonDeserializers.entrySet()) {
                    addDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keySerializers != null) {
                for (Entry<Class<?>, ValueSerializer<?>> entry : keySerializers.entrySet()) {
                    addKeySerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keyDeserializers != null) {
                for (Entry<Class<?>, KeyDeserializer> entry : keyDeserializers.entrySet()) {
                    module.addKeyDeserializer(entry.getKey(), entry.getValue());
                }
            }
            modules.add(module);
        }

        for (JsonSubType jsonSubType : jsonSubTypes) {
            modules.addAll(jsonSubType.modules());
        }

        for (Consumer<JsonMapper.Builder> customizer : customizers) {
            customizer.accept(jsonMapper);
        }

        jsonMapper.addModules(modules);
        return jsonMapper.build();
    }

    //
    // Yes this code is strange.  The addSerializer and addDeserializer methods arguments have
    // generic types that are dependent on each other, but since our map has no type information, we
    // have no type T for casting the type and serializer.  This is why these methods have generic type
    // T but it is only used for casting
    //

    @SuppressWarnings("unchecked")
    private <T> void addSerializer(SimpleModule module, Class<?> type, ValueSerializer<?> jsonSerializer)
    {
        module.addSerializer((Class<? extends T>) type, (ValueSerializer<T>) jsonSerializer);
    }

    @SuppressWarnings("unchecked")
    public <T> void addDeserializer(SimpleModule module, Class<?> type, ValueDeserializer<?> jsonDeserializer)
    {
        module.addDeserializer((Class<T>) type, (ValueDeserializer<? extends T>) jsonDeserializer);
    }

    @SuppressWarnings("unchecked")
    private <T> void addKeySerializer(SimpleModule module, Class<?> type, ValueSerializer<?> keySerializer)
    {
        module.addKeySerializer((Class<? extends T>) type, (ValueSerializer<T>) keySerializer);
    }

    public static Provider<ObjectMapper> toObjectMapperProvider(Provider<JsonMapper> jsonMapperProvider)
    {
        return jsonMapperProvider::get;
    }
}

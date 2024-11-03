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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ObjectMapperProvider
        implements Provider<ObjectMapper>
{
    private final JsonFactory jsonFactory;

    private Map<Class<?>, JsonSerializer<?>> keySerializers;
    private Map<Class<?>, KeyDeserializer> keyDeserializers;
    private Map<Class<?>, JsonSerializer<?>> jsonSerializers;
    private Map<Class<?>, JsonDeserializer<?>> jsonDeserializers;

    private final Set<JsonSubType> jsonSubTypes = new HashSet<>();

    private final Set<Module> modules = new HashSet<>();

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

        modules.add(new Jdk8Module());
        modules.add(new JavaTimeModule());
        modules.add(new GuavaModule());
        modules.add(new ParameterNamesModule());
        modules.add(new RecordAutoDetectModule());

        try {
            getClass().getClassLoader().loadClass("org.joda.time.DateTime");
            modules.add(new JodaModule());
        }
        catch (ClassNotFoundException ignored) {
        }
    }

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers = ImmutableMap.copyOf(jsonSerializers);
    }

    public ObjectMapperProvider withJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        setJsonSerializers(jsonSerializers);
        return this;
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers = ImmutableMap.copyOf(jsonDeserializers);
    }

    public ObjectMapperProvider withJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        setJsonDeserializers(jsonDeserializers);
        return this;
    }

    @Inject(optional = true)
    public void setKeySerializers(@JsonKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
    {
        this.keySerializers = keySerializers;
    }

    public ObjectMapperProvider withKeySerializers(@JsonKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
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
    public void setModules(Set<Module> modules)
    {
        this.modules.addAll(modules);
    }

    public ObjectMapperProvider withModules(Set<Module> modules)
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

    @Override
    public ObjectMapper get()
    {
        JsonMapper.Builder objectMapper = JsonMapper.builder(jsonFactory);

        // ignore unknown fields (for backwards compatibility)
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // do not allow converting a float to an integer
        objectMapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // use ISO dates
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Skip fields that are null or absent (Optional) when serializing objects.
        // This only applies to mapped object fields, not containers like Map or List.
        objectMapper.defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS));

        // disable auto detection of json properties... all properties must be explicit
        objectMapper.disable(MapperFeature.AUTO_DETECT_CREATORS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_FIELDS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_SETTERS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_GETTERS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_IS_GETTERS);
        objectMapper.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
        objectMapper.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        objectMapper.disable(MapperFeature.INFER_PROPERTY_MUTATORS);
        objectMapper.disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS);

        if (jsonSerializers != null || jsonDeserializers != null || keySerializers != null || keyDeserializers != null) {
            SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null, null, null));
            if (jsonSerializers != null) {
                for (Entry<Class<?>, JsonSerializer<?>> entry : jsonSerializers.entrySet()) {
                    addSerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (jsonDeserializers != null) {
                for (Entry<Class<?>, JsonDeserializer<?>> entry : jsonDeserializers.entrySet()) {
                    addDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keySerializers != null) {
                for (Entry<Class<?>, JsonSerializer<?>> entry : keySerializers.entrySet()) {
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

        objectMapper.addModules(modules);

        return objectMapper.build();
    }

    //
    // Yes this code is strange.  The addSerializer and addDeserializer methods arguments have
    // generic types that are dependent on each other, but since our map has no type information, we
    // have no type T for casting the type and serializer.  This is why these methods have generic type
    // T but it is only used for casting
    //

    @SuppressWarnings("unchecked")
    private <T> void addSerializer(SimpleModule module, Class<?> type, JsonSerializer<?> jsonSerializer)
    {
        module.addSerializer((Class<? extends T>) type, (JsonSerializer<T>) jsonSerializer);
    }

    @SuppressWarnings("unchecked")
    public <T> void addDeserializer(SimpleModule module, Class<?> type, JsonDeserializer<?> jsonDeserializer)
    {
        module.addDeserializer((Class<T>) type, (JsonDeserializer<? extends T>) jsonDeserializer);
    }

    @SuppressWarnings("unchecked")
    private <T> void addKeySerializer(SimpleModule module, Class<?> type, JsonSerializer<?> keySerializer)
    {
        module.addKeySerializer((Class<? extends T>) type, (JsonSerializer<T>) keySerializer);
    }
}

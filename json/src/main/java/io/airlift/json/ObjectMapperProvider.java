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
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.json.uuid.CustomUuidDeserializer;
import io.airlift.json.uuid.CustomUuidSerializer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

public class ObjectMapperProvider
        implements Provider<ObjectMapper>
{
    private final Map<Class<?>, JsonSerializer<?>> jsonSerializers = new HashMap<>();
    private final Map<Class<?>, JsonDeserializer<?>> jsonDeserializers = new HashMap<>();
    private final Map<Class<?>, JsonSerializer<?>> keySerializers = new HashMap<>();
    private final Map<Class<?>, KeyDeserializer> keyDeserializers = new HashMap<>();
    private final Set<Module> modules = new HashSet<>();

    public ObjectMapperProvider()
    {
        // add modules for Guava and Joda
        modules.add(new GuavaModule());
        modules.add(new JodaModule());

        // fix performance for UUID
        jsonSerializers.put(UUID.class, new CustomUuidSerializer());
        jsonDeserializers.put(UUID.class, new CustomUuidDeserializer());
    }

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers.putAll(jsonSerializers);
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers.putAll(jsonDeserializers);
    }

    @Inject(optional = true)
    public void setKeySerializers(@JsonKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
    {
        this.keySerializers.putAll(keySerializers);
    }

    @Inject(optional = true)
    public void setKeyDeserializers(@JsonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        this.keyDeserializers.putAll(keyDeserializers);
    }

    @Inject(optional = true)
    public void setModules(Set<Module> modules)
    {
        this.modules.addAll(modules);
    }

    @Override
    public ObjectMapper get()
    {
        ObjectMapper objectMapper = new ObjectMapper();

        // ignore unknown fields (for backwards compatibility)
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // use ISO dates
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // skip fields that are null instead of writing an explicit json null value
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // disable auto detection of json properties... all properties must be explicit
        objectMapper.disable(MapperFeature.AUTO_DETECT_CREATORS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_FIELDS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_SETTERS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_GETTERS);
        objectMapper.disable(MapperFeature.AUTO_DETECT_IS_GETTERS);
        objectMapper.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
        objectMapper.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);

        SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null));
        for (Entry<Class<?>, JsonSerializer<?>> entry : jsonSerializers.entrySet()) {
            addSerializer(module, entry.getKey(), entry.getValue());
        }
        for (Entry<Class<?>, JsonDeserializer<?>> entry : jsonDeserializers.entrySet()) {
            addDeserializer(module, entry.getKey(), entry.getValue());
        }
        for (Entry<Class<?>, JsonSerializer<?>> entry : keySerializers.entrySet()) {
            addKeySerializer(module, entry.getKey(), entry.getValue());
        }
        for (Entry<Class<?>, KeyDeserializer> entry : keyDeserializers.entrySet()) {
            module.addKeyDeserializer(entry.getKey(), entry.getValue());
        }
        modules.add(module);

        for (Module m : modules) {
            objectMapper.registerModule(m);
        }

        return objectMapper;
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

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

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class JsonBinder
{
    private final MapBinder<Class<?>, JsonSerializer<?>> keySerializerMapBinder;
    private final MapBinder<Class<?>, KeyDeserializer> keyDeserializerMapBinder;
    private final MapBinder<Class<?>, JsonSerializer<?>> serializerMapBinder;
    private final MapBinder<Class<?>, JsonDeserializer<?>> deserializerMapBinder;
    private final Multibinder<Module> moduleBinder;

    public static JsonBinder jsonBinder(Binder binder)
    {
        return new JsonBinder(binder);
    }

    private JsonBinder(Binder binder)
    {
        binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        keySerializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<JsonSerializer<?>>() {}, JsonKeySerde.class);
        keyDeserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<KeyDeserializer>() {}, JsonKeySerde.class);
        serializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<JsonSerializer<?>>() {});
        deserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<JsonDeserializer<?>>() {});
        moduleBinder = newSetBinder(binder, Module.class);
    }

    public LinkedBindingBuilder<JsonSerializer<?>> addKeySerializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return keySerializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<KeyDeserializer> addKeyDeserializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return keyDeserializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<JsonSerializer<?>> addSerializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return serializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<JsonDeserializer<?>> addDeserializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return deserializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<Module> addModuleBinding()
    {
        return moduleBinder.addBinding();
    }

    public <T> void bindSerializer(JsonSerializer<T> jsonSerializer)
    {
        requireNonNull(jsonSerializer, "jsonSerializer is null");

        Class<?> type = jsonSerializer.handledType();
        requireNonNull(type, "jsonSerializer.handledType is null");
        Preconditions.checkArgument(type == Object.class, "jsonSerializer.handledType can not be Object.class");
        serializerMapBinder.addBinding(type).toInstance(jsonSerializer);
    }
}

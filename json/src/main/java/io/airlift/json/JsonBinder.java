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

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class JsonBinder
{
    private final MapBinder<Class<?>, ValueSerializer<?>> keySerializerMapBinder;
    private final MapBinder<Class<?>, KeyDeserializer> keyDeserializerMapBinder;
    private final MapBinder<Class<?>, ValueSerializer<?>> serializerMapBinder;
    private final MapBinder<Class<?>, ValueDeserializer<?>> deserializerMapBinder;
    private final Multibinder<Module> moduleBinder;

    public static JsonBinder jsonBinder(Binder binder)
    {
        return new JsonBinder(binder);
    }

    private JsonBinder(Binder binder)
    {
        binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        keySerializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, JsonKeySerde.class);
        keyDeserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, JsonKeySerde.class);
        serializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});
        deserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});
        moduleBinder = newSetBinder(binder, Module.class);
    }

    public LinkedBindingBuilder<ValueSerializer<?>> addKeySerializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return keySerializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<KeyDeserializer> addKeyDeserializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return keyDeserializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<ValueSerializer<?>> addSerializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return serializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<ValueDeserializer<?>> addDeserializerBinding(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return deserializerMapBinder.addBinding(type);
    }

    public LinkedBindingBuilder<Module> addModuleBinding()
    {
        return moduleBinder.addBinding();
    }

    public <T> void bindSerializer(ValueSerializer<T> jsonSerializer)
    {
        requireNonNull(jsonSerializer, "jsonSerializer is null");

        Class<?> type = jsonSerializer.handledType();
        requireNonNull(type, "jsonSerializer.handledType is null");
        checkArgument(type == Object.class, "jsonSerializer.handledType can not be Object.class");
        serializerMapBinder.addBinding(type).toInstance(jsonSerializer);
    }
}

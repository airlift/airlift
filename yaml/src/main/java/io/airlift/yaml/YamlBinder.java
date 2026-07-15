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

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

/**
 * Binds Jackson customizations for the YAML mapper. YAML keeps its own multibinders, separate
 * from {@code io.airlift.json.JsonBinder}, so a module or serializer registered for one format
 * does not silently apply to the other. Register YAML customizations here and JSON ones through
 * {@code JsonBinder}. (Polymorphic subtypes remain shared via
 * {@link io.airlift.jackson.JacksonSubTypeBinder}, since they describe Java types, not formats.)
 */
public class YamlBinder
{
    private final MapBinder<Class<?>, JsonSerializer<?>> keySerializerMapBinder;
    private final MapBinder<Class<?>, KeyDeserializer> keyDeserializerMapBinder;
    private final MapBinder<Class<?>, JsonSerializer<?>> serializerMapBinder;
    private final MapBinder<Class<?>, JsonDeserializer<?>> deserializerMapBinder;
    private final Multibinder<Module> moduleBinder;

    public static YamlBinder yamlBinder(Binder binder)
    {
        return new YamlBinder(binder);
    }

    private YamlBinder(Binder binder)
    {
        binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        keySerializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, YamlKeySerde.class);
        keyDeserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, YamlKeySerde.class);
        serializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, Yaml.class);
        deserializerMapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {}, Yaml.class);
        moduleBinder = newSetBinder(binder, new TypeLiteral<Module>() {}, Yaml.class);
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
        checkArgument(type == Object.class, "jsonSerializer.handledType can not be Object.class");
        serializerMapBinder.addBinding(type).toInstance(jsonSerializer);
    }
}

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
import com.google.inject.binder.LinkedBindingBuilder;
import io.airlift.json.JsonBinder;

import static io.airlift.json.JsonBinder.jsonBinder;

/**
 * Facade over {@link JsonBinder} for callers that want to bind Jackson customizations from
 * code that already works with YAML. Bindings registered here go into the same multibinders the
 * JSON mapper reads. Modules, serializers, deserializers, and key serdes registered through
 * either binder are visible to both the {@code JsonMapper} and the {@code YAMLMapper}.
 *
 * <p>If a need arises to bind a customization to one format only we can add a format-qualified
 * binding path instead of separate binders.
 */
public class YamlBinder
{
    private final JsonBinder delegate;

    public static YamlBinder yamlBinder(Binder binder)
    {
        return new YamlBinder(binder);
    }

    private YamlBinder(Binder binder)
    {
        this.delegate = jsonBinder(binder);
    }

    public LinkedBindingBuilder<JsonSerializer<?>> addKeySerializerBinding(Class<?> type)
    {
        return delegate.addKeySerializerBinding(type);
    }

    public LinkedBindingBuilder<KeyDeserializer> addKeyDeserializerBinding(Class<?> type)
    {
        return delegate.addKeyDeserializerBinding(type);
    }

    public LinkedBindingBuilder<JsonSerializer<?>> addSerializerBinding(Class<?> type)
    {
        return delegate.addSerializerBinding(type);
    }

    public LinkedBindingBuilder<JsonDeserializer<?>> addDeserializerBinding(Class<?> type)
    {
        return delegate.addDeserializerBinding(type);
    }

    public LinkedBindingBuilder<Module> addModuleBinding()
    {
        return delegate.addModuleBinding();
    }

    public <T> void bindSerializer(JsonSerializer<T> jsonSerializer)
    {
        delegate.bindSerializer(jsonSerializer);
    }
}

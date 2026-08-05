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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class YamlCodecBinder
{
    private final Binder binder;

    public static YamlCodecBinder yamlCodecBinder(Binder binder)
    {
        return new YamlCodecBinder(binder);
    }

    private YamlCodecBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
    }

    public void bindYamlCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getYamlCodecKey(type)).toProvider(new YamlCodecProvider(type)).in(Scopes.SINGLETON);
    }

    public void bindYamlCodec(TypeLiteral<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getYamlCodecKey(type.getType())).toProvider(new YamlCodecProvider(type.getType())).in(Scopes.SINGLETON);
    }

    public void bindListYamlCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type);
        binder.bind(getYamlCodecKey(listType)).toProvider(new YamlCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindListYamlCodec(YamlCodec<?> type)
    {
        requireNonNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type.getTypeToken().getType());
        binder.bind(getYamlCodecKey(listType)).toProvider(new YamlCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindMapYamlCodec(Class<?> keyType, Class<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        binder.bind(getYamlCodecKey(mapType)).toProvider(new YamlCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    public void bindMapYamlCodec(Class<?> keyType, YamlCodec<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType.getTypeToken().getType());
        binder.bind(getYamlCodecKey(mapType)).toProvider(new YamlCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    @SuppressWarnings("unchecked")
    private Key<YamlCodec<?>> getYamlCodecKey(Type type)
    {
        return (Key<YamlCodec<?>>) Key.get(new ParameterizedTypeImpl(null, YamlCodec.class, type));
    }
}

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
package io.airlift.json;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes;

import javax.inject.Provider;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.Objects.requireNonNull;

public abstract class CodecBinder
{
    private final Binder binder;
    private final Function<Type, Provider<Codec<?>>> codecProviderFactory;

    public CodecBinder(Binder binder, Function<Type, Provider<Codec<?>>> codecProviderFactory)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.codecProviderFactory = requireNonNull(codecProviderFactory, "codecProviderFactory is null");
    }

    public void bindCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getCodecKey(type)).toProvider(codecProviderFactory.apply(type)).in(SINGLETON);
    }

    public void bindCodec(TypeLiteral<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getCodecKey(type.getType())).toProvider(new JsonCodecProvider(type.getType())).in(Scopes.SINGLETON);
    }

    public void bindListCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        MoreTypes.ParameterizedTypeImpl listType = new MoreTypes.ParameterizedTypeImpl(null, List.class, type);
        binder.bind(getCodecKey(listType)).toProvider(codecProviderFactory.apply(listType)).in(SINGLETON);
    }

    public void bindListCodec(Codec<?> type)
    {
        requireNonNull(type, "type is null");

        MoreTypes.ParameterizedTypeImpl listType = new MoreTypes.ParameterizedTypeImpl(null, List.class, type.getType());
        binder.bind(getCodecKey(listType)).toProvider(codecProviderFactory.apply(listType)).in(SINGLETON);
    }

    public void bindMapCodec(Class<?> keyType, Class<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        MoreTypes.ParameterizedTypeImpl mapType = new MoreTypes.ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        binder.bind(getCodecKey(mapType)).toProvider(codecProviderFactory.apply(mapType)).in(SINGLETON);
    }

    public void bindMapCodec(Class<?> keyType, Codec<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        MoreTypes.ParameterizedTypeImpl mapType = new MoreTypes.ParameterizedTypeImpl(null, Map.class, keyType, valueType.getType());
        binder.bind(getCodecKey(mapType)).toProvider(codecProviderFactory.apply(mapType)).in(SINGLETON);
    }

    abstract Key<Codec<?>> getCodecKey(Type type);
}

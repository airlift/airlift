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
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class JsonCodecBinder
{
    private final Binder binder;

    public static JsonCodecBinder jsonCodecBinder(Binder binder)
    {
        return new JsonCodecBinder(binder);
    }

    private JsonCodecBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
    }

    public void bindJsonCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getJsonCodecKey(type)).toProvider(new JsonCodecProvider(type)).in(Scopes.SINGLETON);
    }

    public void bindJsonCodec(TypeLiteral<?> type)
    {
        requireNonNull(type, "type is null");

        binder.bind(getJsonCodecKey(type.getType())).toProvider(new JsonCodecProvider(type.getType())).in(Scopes.SINGLETON);
    }

    public void bindListJsonCodec(Class<?> type)
    {
        requireNonNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type);
        binder.bind(getJsonCodecKey(listType)).toProvider(new JsonCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindListJsonCodec(JsonCodec<?> type)
    {
        requireNonNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type.getType());
        binder.bind(getJsonCodecKey(listType)).toProvider(new JsonCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindMapJsonCodec(Class<?> keyType, Class<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        binder.bind(getJsonCodecKey(mapType)).toProvider(new JsonCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    public void bindMapJsonCodec(Class<?> keyType, JsonCodec<?> valueType)
    {
        requireNonNull(keyType, "keyType is null");
        requireNonNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType.getType());
        binder.bind(getJsonCodecKey(mapType)).toProvider(new JsonCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    private Key<JsonCodec<?>> getJsonCodecKey(Type type)
    {
        return (Key<JsonCodec<?>>) Key.get(new ParameterizedTypeImpl(null, JsonCodec.class, type));
    }
}

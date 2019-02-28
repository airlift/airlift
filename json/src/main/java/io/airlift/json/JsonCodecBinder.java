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
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;

public class JsonCodecBinder
        extends CodecBinder
{
    public static JsonCodecBinder jsonCodecBinder(Binder binder)
    {
        return new JsonCodecBinder(binder);
    }

    private JsonCodecBinder(Binder binder)
    {
        super(binder, (type) -> new JsonCodecProvider(type));
    }

    @Deprecated
    public void bindJsonCodec(Class<?> type)
    {
        bindCodec(type);
    }

    @Deprecated
    public void bindJsonCodec(TypeLiteral<?> type)
    {
        bindCodec(type);
    }

    @Deprecated
    public void bindListJsonCodec(Class<?> type)
    {
        bindListCodec(type);
    }

    @Deprecated
    public void bindListJsonCodec(JsonCodec<?> type)
    {
        bindListCodec(type);
    }

    @Deprecated
    public void bindMapJsonCodec(Class<?> keyType, Class<?> valueType)
    {
        bindMapCodec(keyType, valueType);
    }

    @Deprecated
    public void bindMapJsonCodec(Class<?> keyType, JsonCodec<?> valueType)
    {
        bindMapCodec(keyType, valueType);
    }

    @SuppressWarnings("unchecked")
    @Override
    Key<Codec<?>> getCodecKey(Type type)
    {
        return (Key<Codec<?>>) Key.get(new ParameterizedTypeImpl(null, JsonCodec.class, type));
    }
}

package com.proofpoint.json;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class JsonCodecBinder
{
    private final Binder binder;

    public static JsonCodecBinder jsonCodecBinder(Binder binder)
    {
        return new JsonCodecBinder(binder);
    }

    private JsonCodecBinder(Binder binder)
    {
        this.binder = binder;
    }

    public void bindJsonCodec(Class<?> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        binder.bind(getJsonCodecKey(type)).toProvider(new JsonCodecProvider(type)).in(Scopes.SINGLETON);
    }

    public void bindJsonCodec(TypeLiteral<?> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        binder.bind(getJsonCodecKey(type.getType())).toProvider(new JsonCodecProvider(type.getType())).in(Scopes.SINGLETON);
    }

    public void bindListJsonCodec(Class<?> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type);
        binder.bind(getJsonCodecKey(listType)).toProvider(new JsonCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindListJsonCodec(JsonCodec<?> type)
    {
        Preconditions.checkNotNull(type, "type is null");

        ParameterizedTypeImpl listType = new ParameterizedTypeImpl(null, List.class, type.getType());
        binder.bind(getJsonCodecKey(listType)).toProvider(new JsonCodecProvider(listType)).in(Scopes.SINGLETON);
    }

    public void bindMapJsonCodec(Class<?> keyType, Class<?> valueType)
    {
        Preconditions.checkNotNull(keyType, "keyType is null");
        Preconditions.checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType);
        binder.bind(getJsonCodecKey(mapType)).toProvider(new JsonCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    public void bindMapJsonCodec(Class<?> keyType, JsonCodec<?> valueType)
    {
        Preconditions.checkNotNull(keyType, "keyType is null");
        Preconditions.checkNotNull(valueType, "valueType is null");

        ParameterizedTypeImpl mapType = new ParameterizedTypeImpl(null, Map.class, keyType, valueType.getType());
        binder.bind(getJsonCodecKey(mapType)).toProvider(new JsonCodecProvider(mapType)).in(Scopes.SINGLETON);
    }

    private Key<JsonCodec<?>> getJsonCodecKey(Type type)
    {
        return (Key<JsonCodec<?>>) Key.get(new ParameterizedTypeImpl(null, JsonCodec.class, type));
    }
}

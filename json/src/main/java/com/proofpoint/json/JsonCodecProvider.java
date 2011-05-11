package com.proofpoint.json;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.lang.reflect.Type;

class JsonCodecProvider implements Provider<JsonCodec<?>>
{
    private final Type type;
    private JsonCodecFactory jsonCodecFactory;

    public JsonCodecProvider(Type type)
    {
        this.type = type;
    }

    @Inject
    public void setJsonCodecFactory(JsonCodecFactory jsonCodecFactory)
    {
        this.jsonCodecFactory = jsonCodecFactory;
    }

    @Override
    public JsonCodec<?> get()
    {
        return jsonCodecFactory.jsonCodec(type);
    }
}

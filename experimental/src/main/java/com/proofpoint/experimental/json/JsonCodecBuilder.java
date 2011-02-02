package com.proofpoint.experimental.json;

import com.google.inject.TypeLiteral;

public class JsonCodecBuilder
{
    private final boolean prettyPrint;

    public JsonCodecBuilder()
    {
        prettyPrint = false;
    }

    private JsonCodecBuilder(boolean prettyPrint)
    {
        this.prettyPrint = prettyPrint;
    }

    public JsonCodecBuilder prettyPrint()
    {
        return new JsonCodecBuilder(true);
    }

    public <T> JsonCodec<T> build(Class<T> type)
    {
        return new JacksonJsonCodec<T>(type, prettyPrint);
    }

    public <T> JsonCodec<T> build(TypeLiteral<T> type)
    {
        return new JacksonJsonCodec<T>(type.getType(), prettyPrint);
    }
}

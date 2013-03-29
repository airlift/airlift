package com.proofpoint.json.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;

import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.json.JsonCodec.jsonCodec;

public class JsonTester
{
    private JsonTester()
    {
    }

    public static <T> T decodeJson(Class<T> tClass, Object value)
    {
        return decodeJson(jsonCodec(tClass), value);
    }

    public static <T> T decodeJson(JsonCodec<T> codec, Object value)
    {
        final String json;
        try {
            json = new ObjectMapper().writeValueAsString(value);
        }
        catch (IOException e) {
            throw propagate(e);
        }
        return codec.fromJson(json);
    }
}

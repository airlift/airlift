package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.proofpoint.json.JsonCodec;

import java.io.OutputStream;

@Beta
public class JsonBodyGenerator<T> implements BodyGenerator
{
    public static <T> JsonBodyGenerator<T> jsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        return new JsonBodyGenerator<T>(jsonCodec, instance);
    }

    private byte[] json;

    private JsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        json = jsonCodec.toJson(instance).getBytes(Charsets.UTF_8);
    }

    @Override
    public void write(OutputStream out)
            throws Exception
    {
        out.write(json);
    }
}

package io.airlift.http.client;

import java.io.OutputStream;
import java.nio.charset.Charset;

public class StaticBodyGenerator implements BodyGenerator
{
    public static StaticBodyGenerator createStaticBodyGenerator(String body, Charset charset)
    {
        return new StaticBodyGenerator(body.getBytes(charset));
    }

    public static StaticBodyGenerator createStaticBodyGenerator(byte[] body)
    {
        return new StaticBodyGenerator(body);
    }

    private final byte[] body;

    private StaticBodyGenerator(byte[] body)
    {
        this.body = body;
    }

    @Override
    public void write(OutputStream out)
            throws Exception
    {
        out.write(body);
    }
}

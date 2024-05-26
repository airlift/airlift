package io.airlift.http.client;

import java.io.InputStream;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

public class StreamingBodyGenerator
        implements BodyGenerator
{
    private final InputStream source;

    public static StreamingBodyGenerator streamingBodyGenerator(InputStream source)
    {
        return new StreamingBodyGenerator(source);
    }

    public InputStream source()
    {
        return source;
    }

    private StreamingBodyGenerator(InputStream source)
    {
        this.source = requireNonNull(source, "source is null");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void write(OutputStream out)
            throws Exception
    {
        throw new UnsupportedOperationException();
    }
}

package io.airlift.http.client;

import java.io.InputStream;

import static java.util.Objects.requireNonNull;

public final class StreamingBodyGenerator
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
}

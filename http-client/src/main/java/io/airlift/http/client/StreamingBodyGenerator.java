package io.airlift.http.client;

import com.google.common.net.MediaType;

import java.io.InputStream;

import static com.google.common.net.MediaType.APPLICATION_BINARY;
import static java.util.Objects.requireNonNull;

public final class StreamingBodyGenerator
        implements BodyGenerator
{
    private final InputStream source;
    private final MediaType contentType;

    public static StreamingBodyGenerator streamingBodyGenerator(InputStream source)
    {
        return new StreamingBodyGenerator(APPLICATION_BINARY, source);
    }

    public static StreamingBodyGenerator streamingBodyGenerator(MediaType contentType, InputStream source)
    {
        return new StreamingBodyGenerator(contentType, source);
    }

    public InputStream source()
    {
        return source;
    }

    public MediaType contentType()
    {
        return contentType;
    }

    private StreamingBodyGenerator(MediaType contentType, InputStream source)
    {
        this.contentType = requireNonNull(contentType, "contentType is null");
        this.source = requireNonNull(source, "source is null");
    }
}

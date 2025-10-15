package io.airlift.http.client;

import static com.google.common.net.MediaType.APPLICATION_BINARY;
import static java.util.Objects.requireNonNull;

import com.google.common.net.MediaType;
import java.io.InputStream;

public final class StreamingBodyGenerator implements BodyGenerator {
    private final InputStream source;
    private final String contentType;

    public static StreamingBodyGenerator streamingBodyGenerator(InputStream source) {
        return new StreamingBodyGenerator(APPLICATION_BINARY, source);
    }

    public static StreamingBodyGenerator streamingBodyGenerator(MediaType contentType, InputStream source) {
        return new StreamingBodyGenerator(contentType, source);
    }

    public InputStream source() {
        return source;
    }

    public String contentType() {
        return contentType;
    }

    private StreamingBodyGenerator(MediaType contentType, InputStream source) {
        this.contentType = requireNonNull(contentType, "contentType is null").toString();
        this.source = requireNonNull(source, "source is null");
    }
}

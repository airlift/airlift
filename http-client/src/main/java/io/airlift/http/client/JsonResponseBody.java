package io.airlift.http.client;

import io.airlift.http.client.Response.InputStreamContent;
import io.airlift.http.client.jetty.SegmentedInputStream;
import io.airlift.json.JsonCodec;

import java.io.FilterInputStream;
import java.io.InputStream;

import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static java.util.Objects.requireNonNull;

/**
 * Response body that JSON is parsed from in place. A body buffered in memory by the Jetty client
 * is joined into one array only when the bytes are requested. Small bodies and any other body
 * are joined up front.
 */
final class JsonResponseBody
{
    // Avoid allocating Jackson's input buffer for small segmented bodies, especially on virtual threads.
    private static final int MAX_ARRAY_BODY_SIZE = 8 * 1024;

    private final int length;
    private SegmentedInputStream stream;
    private byte[] bytes;

    private JsonResponseBody(SegmentedInputStream stream)
    {
        this.stream = stream;
        this.length = stream.available();
        stream.mark(length);
    }

    private JsonResponseBody(byte[] bytes)
    {
        this.bytes = requireNonNull(bytes, "bytes is null");
        this.length = bytes.length;
    }

    static JsonResponseBody of(byte[] bytes)
    {
        return new JsonResponseBody(bytes);
    }

    static JsonResponseBody of(Request request, Response response)
    {
        if (response.getContent() instanceof InputStreamContent(SegmentedInputStream stream) && stream.available() > MAX_ARRAY_BODY_SIZE) {
            return new JsonResponseBody(stream);
        }
        return new JsonResponseBody(getResponseBytes(request, response));
    }

    synchronized <T> T parseJson(JsonCodec<T> jsonCodec)
    {
        if (bytes != null) {
            return jsonCodec.fromJson(bytes);
        }
        stream.reset();
        try (ParsingInputStream input = new ParsingInputStream(stream)) {
            return jsonCodec.fromJson(input);
        }
    }

    int length()
    {
        return length;
    }

    synchronized byte[] bytes()
    {
        if (bytes == null) {
            stream.reset();
            bytes = stream.readAllBytes();
            stream = null;
        }
        return bytes;
    }

    // Jackson exceptions can retain their parser and its input source. Detach the borrowed stream
    // on close so the exception does not keep the segments alive after the body is materialized.
    private static final class ParsingInputStream
            extends FilterInputStream
    {
        private ParsingInputStream(InputStream input)
        {
            super(input);
        }

        @Override
        public void close()
        {
            in = null;
        }
    }
}

package io.airlift.http.client;

import io.airlift.http.client.Response.InputStreamContent;
import io.airlift.http.client.jetty.SegmentedInputStream;
import io.airlift.json.JsonCodec;

import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static java.util.Objects.requireNonNull;

/**
 * Response body that JSON is parsed from in place. A body buffered in memory by the Jetty client
 * is joined into one array only when the bytes are requested. Any other body is joined up front.
 */
final class JsonResponseBody
{
    private final SegmentedInputStream stream;
    private final int length;
    private byte[] bytes;

    private JsonResponseBody(SegmentedInputStream stream)
    {
        this.stream = stream;
        this.length = stream.available();
        stream.mark(length);
    }

    private JsonResponseBody(byte[] bytes)
    {
        this.stream = null;
        this.bytes = requireNonNull(bytes, "bytes is null");
        this.length = bytes.length;
    }

    static JsonResponseBody of(byte[] bytes)
    {
        return new JsonResponseBody(bytes);
    }

    static JsonResponseBody of(Request request, Response response)
    {
        if (response.getContent() instanceof InputStreamContent(SegmentedInputStream stream)) {
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
        return jsonCodec.fromJson(stream);
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
        }
        return bytes;
    }
}

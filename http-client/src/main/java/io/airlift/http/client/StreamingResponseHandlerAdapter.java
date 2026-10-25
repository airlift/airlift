package io.airlift.http.client;

import io.airlift.http.client.StreamingResponseHandler.BodyConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static com.google.common.io.Closeables.closeQuietly;
import static java.util.Objects.requireNonNull;

/**
 * Runs a streaming handler over a buffered response.
 */
class StreamingResponseHandlerAdapter<T, E extends Exception>
        implements ResponseHandler<T, E>
{
    private static final int BUFFER_SIZE = 8192;

    private final StreamingResponseHandler<T, E> handler;

    StreamingResponseHandlerAdapter(StreamingResponseHandler<T, E> handler)
    {
        this.handler = requireNonNull(handler, "handler is null");
    }

    @Override
    public T handleException(Request request, Exception exception)
            throws E
    {
        return handler.handleException(request, exception);
    }

    @Override
    public T handle(Request request, Response response)
            throws E
    {
        BodyConsumer<T, E> consumer = requireNonNull(handler.handleHeaders(request, response), "body consumer is null");
        InputStream inputStream;
        try {
            inputStream = response.getInputStream();
        }
        catch (IOException e) {
            return handler.handleException(request, e);
        }
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                int count;
                try {
                    count = inputStream.read(buffer);
                }
                catch (IOException e) {
                    return handler.handleException(request, e);
                }
                if (count < 0) {
                    return consumer.complete();
                }
                consumer.accept(ByteBuffer.wrap(buffer, 0, count));
            }
        }
        finally {
            closeQuietly(inputStream);
        }
    }
}

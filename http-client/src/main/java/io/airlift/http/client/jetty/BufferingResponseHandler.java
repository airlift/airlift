package io.airlift.http.client.jetty;

import com.google.common.primitives.Longs;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.StreamingResponseHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static io.airlift.http.client.HeaderNames.CONTENT_LENGTH;
import static java.util.Objects.requireNonNull;

/**
 * Copies each response chunk once into heap segments that are handed to the response handler as is.
 * Segments never exceed a fixed cap. With Content-Length they cover the body exactly, so a body within
 * the cap is one segment of that size. Without it each new segment is as large as the body received
 * so far, up to the cap, so only the last segment has unused space.
 */
class BufferingResponseHandler<T, E extends Exception>
        implements StreamingResponseHandler<T, E>
{
    // Stays below the G1 humongous threshold on any heap
    private static final int MAX_SEGMENT_SIZE = 256 * 1024;

    private final ResponseHandler<T, E> responseHandler;

    public BufferingResponseHandler(ResponseHandler<T, E> responseHandler)
    {
        this.responseHandler = requireNonNull(responseHandler, "responseHandler is null");
    }

    @Override
    public T handleException(Request request, Exception exception)
            throws E
    {
        return responseHandler.handleException(request, exception);
    }

    @Override
    public BodyConsumer<T, E> handleHeaders(Request request, ResponseMetadata response)
    {
        return new BufferingBodyConsumer(request, response);
    }

    private class BufferingBodyConsumer
            implements BodyConsumer<T, E>
    {
        private final Request request;
        private final ResponseMetadata response;
        private final long contentLength;
        private final List<byte[]> segments = new ArrayList<>();
        private int lastSegmentLength;
        private int totalLength;

        private BufferingBodyConsumer(Request request, ResponseMetadata response)
        {
            this.request = requireNonNull(request, "request is null");
            this.response = requireNonNull(response, "response is null");
            this.contentLength = response.getHeader(CONTENT_LENGTH)
                    .map(Longs::tryParse)
                    .orElse(-1L);
        }

        @Override
        public void accept(ByteBuffer content)
        {
            int remaining = content.remaining();
            while (remaining > 0) {
                if (segments.isEmpty() || lastSegmentLength == segments.getLast().length) {
                    segments.add(new byte[nextSegmentSize(remaining)]);
                    lastSegmentLength = 0;
                }
                byte[] segment = segments.getLast();
                int count = Math.min(remaining, segment.length - lastSegmentLength);
                content.get(segment, lastSegmentLength, count);
                lastSegmentLength += count;
                totalLength += count;
                remaining -= count;
            }
        }

        private int nextSegmentSize(int remaining)
        {
            if (contentLength > totalLength) {
                return (int) Math.min(contentLength - totalLength, MAX_SEGMENT_SIZE);
            }
            return Math.min(Math.max(remaining, totalLength), MAX_SEGMENT_SIZE);
        }

        @Override
        public T complete()
                throws E
        {
            ResponseBody body;
            if (totalLength == 0) {
                body = ResponseBody.EMPTY;
            }
            else {
                body = new ResponseBody(segments, totalLength);
            }
            return responseHandler.handle(request, new JettyResponse(response, body));
        }
    }
}

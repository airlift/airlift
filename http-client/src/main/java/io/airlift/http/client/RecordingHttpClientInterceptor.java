/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.RecordedExchange.CaptureState;
import io.airlift.http.client.RecordedExchange.CapturedBody;
import io.airlift.http.client.RecordedExchange.RecordedFailure;
import io.airlift.http.client.RecordedExchange.RecordedRequest;
import io.airlift.http.client.RecordedExchange.RecordedResponse;
import io.airlift.units.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.RecordedExchange.CURRENT_VERSION;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

public final class RecordingHttpClientInterceptor
        implements HttpClientInterceptor
{
    private final int maxBodyBytes;
    private final RecordedExchangeSanitizer sanitizer;
    private final Consumer<RecordedExchange> sink;
    private final AtomicLong nextSequence = new AtomicLong();

    public RecordingHttpClientInterceptor(DataSize maxBodySize, RecordedExchangeSanitizer sanitizer, Consumer<RecordedExchange> sink)
    {
        long maxBodyBytes = requireNonNull(maxBodySize, "maxBodySize is null").toBytes();
        checkArgument(maxBodyBytes >= 0 && maxBodyBytes <= Integer.MAX_VALUE, "maxBodySize must be between 0 and %s bytes", Integer.MAX_VALUE);
        this.maxBodyBytes = (int) maxBodyBytes;
        this.sanitizer = RecordedExchangeSanitizer.redactSensitiveHeaders()
                .andThen(requireNonNull(sanitizer, "sanitizer is null"));
        this.sink = requireNonNull(sink, "sink is null");
    }

    @Override
    public StreamingResponse intercept(Chain chain)
    {
        requireNonNull(chain, "chain is null");
        long sequence = nextSequence.incrementAndGet();
        RecordedRequest request = captureRequest(chain.request());
        CaptureContext context = new CaptureContext(sequence, request);

        StreamingResponse response;
        try {
            response = requireNonNull(chain.proceed(chain.request()), "chain returned null response");
        }
        catch (Throwable failure) {
            context.publish(Optional.empty(), Optional.of(captureFailure(failure)));
            throw failure;
        }

        try {
            return new RecordingStreamingResponse(response, context);
        }
        catch (Throwable failure) {
            response.close();
            throw failure;
        }
    }

    private RecordedRequest captureRequest(Request request)
    {
        return new RecordedRequest(
                request.getMethod(),
                request.getUri().toASCIIString(),
                copyHeaders(request.getHeaders()),
                captureRequestBody(request.getBodyGenerator()));
    }

    private CapturedBody captureRequestBody(BodyGenerator bodyGenerator)
    {
        return switch (bodyGenerator) {
            case null -> new CapturedBody(new byte[0], CaptureState.COMPLETE, false);
            case StaticBodyGenerator generator -> captureBytes(generator.getBody(), CaptureState.COMPLETE);
            case ByteBufferBodyGenerator generator -> captureByteBuffers(generator.getByteBuffers());
            case FileBodyGenerator _, StreamingBodyGenerator _ -> new CapturedBody(new byte[0], CaptureState.UNSUPPORTED, false);
        };
    }

    private CapturedBody captureByteBuffers(ByteBuffer[] byteBuffers)
    {
        BodyCapture capture = new BodyCapture(maxBodyBytes);
        for (ByteBuffer byteBuffer : byteBuffers) {
            capture.append(requireNonNull(byteBuffer, "byteBuffer is null").duplicate());
        }
        return capture.snapshot(CaptureState.COMPLETE);
    }

    private CapturedBody captureBytes(byte[] bytes, CaptureState state)
    {
        int capturedLength = min(bytes.length, maxBodyBytes);
        byte[] captured = new byte[capturedLength];
        System.arraycopy(bytes, 0, captured, 0, capturedLength);
        return new CapturedBody(captured, state, bytes.length > maxBodyBytes);
    }

    private static Map<String, List<String>> copyHeaders(ListMultimap<HeaderName, String> headers)
    {
        ImmutableMap.Builder<String, List<String>> copy = ImmutableMap.builder();
        headers.asMap().forEach((name, values) -> copy.put(name.toString(), ImmutableList.copyOf(values)));
        return copy.buildOrThrow();
    }

    private static RecordedFailure captureFailure(Throwable throwable)
    {
        Throwable cause = requireNonNull(throwable, "throwable is null");
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return new RecordedFailure(cause.getClass().getName(), Optional.ofNullable(cause.getMessage()).orElse(""));
    }

    private final class CaptureContext
    {
        private final long sequence;
        private final RecordedRequest request;
        private final AtomicBoolean published = new AtomicBoolean();

        private CaptureContext(long sequence, RecordedRequest request)
        {
            this.sequence = sequence;
            this.request = requireNonNull(request, "request is null");
        }

        private void publish(Optional<RecordedResponse> response, Optional<RecordedFailure> failure)
        {
            if (published.compareAndSet(false, true)) {
                RecordedExchange rawExchange = new RecordedExchange(CURRENT_VERSION, sequence, request, response, failure);
                sink.accept(requireNonNull(sanitizer.sanitize(rawExchange), "sanitizer returned null"));
            }
        }
    }

    private final class RecordingStreamingResponse
            implements StreamingResponse
    {
        private final StreamingResponse delegate;
        private final CaptureContext context;
        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final Response.Content content;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final BodyCapture capture;

        private RecordingStreamingResponse(StreamingResponse delegate, CaptureContext context)
        {
            this.delegate = requireNonNull(delegate, "delegate is null");
            this.context = requireNonNull(context, "context is null");
            this.statusCode = delegate.getStatusCode();
            this.headers = copyHeaders(delegate.getHeaders());

            this.content = switch (delegate.getContent()) {
                case Response.BytesContent(byte[] bytes) -> {
                    CapturedBody body = captureBytes(bytes, CaptureState.COMPLETE);
                    context.publish(Optional.of(recordedResponse(body)), Optional.empty());
                    this.capture = null;
                    yield new Response.BytesContent(bytes);
                }
                case Response.InputStreamContent(InputStream inputStream) -> {
                    this.capture = new BodyCapture(maxBodyBytes);
                    yield new Response.InputStreamContent(new RecordingInputStream(inputStream, this));
                }
            };
        }

        @Override
        public HttpVersion getHttpVersion()
        {
            return delegate.getHttpVersion();
        }

        @Override
        public int getStatusCode()
        {
            return statusCode;
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return delegate.getHeaders();
        }

        @Override
        public Response.Content getContent()
        {
            return content;
        }

        @Override
        public InputStream getInputStream()
        {
            return switch (content) {
                case Response.BytesContent(byte[] bytes) -> new ByteArrayInputStream(bytes);
                case Response.InputStreamContent(InputStream inputStream) -> inputStream;
            };
        }

        @Override
        public long getBytesRead()
        {
            return delegate.getBytesRead();
        }

        @Override
        public void close()
        {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            try {
                delegate.close();
            }
            catch (Throwable failure) {
                publish(CaptureState.INCOMPLETE, Optional.of(captureFailure(failure)));
                throw failure;
            }
            publish(CaptureState.INCOMPLETE, Optional.empty());
        }

        private void streamComplete()
        {
            publish(CaptureState.COMPLETE, Optional.empty());
        }

        private void streamClosed()
        {
            publish(CaptureState.INCOMPLETE, Optional.empty());
        }

        private void streamFailed(IOException failure)
        {
            publish(CaptureState.INCOMPLETE, Optional.of(captureFailure(failure)));
        }

        private void publish(CaptureState state, Optional<RecordedFailure> failure)
        {
            if (capture != null) {
                context.publish(Optional.of(recordedResponse(capture.snapshot(state))), failure);
            }
        }

        private RecordedResponse recordedResponse(CapturedBody body)
        {
            return new RecordedResponse(statusCode, headers, body);
        }
    }

    private static final class RecordingInputStream
            extends FilterInputStream
    {
        private final RecordingStreamingResponse response;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RecordingInputStream(InputStream delegate, RecordingStreamingResponse response)
        {
            super(requireNonNull(delegate, "delegate is null"));
            this.response = requireNonNull(response, "response is null");
        }

        @Override
        public int read()
                throws IOException
        {
            try {
                int value = super.read();
                if (value < 0) {
                    response.streamComplete();
                }
                else {
                    response.capture.append((byte) value);
                }
                return value;
            }
            catch (IOException failure) {
                response.streamFailed(failure);
                throw failure;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException
        {
            try {
                int read = super.read(bytes, offset, length);
                if (read < 0) {
                    response.streamComplete();
                }
                else {
                    response.capture.append(bytes, offset, read);
                }
                return read;
            }
            catch (IOException failure) {
                response.streamFailed(failure);
                throw failure;
            }
        }

        @Override
        public long skip(long bytes)
                throws IOException
        {
            if (bytes <= 0) {
                return 0;
            }

            byte[] buffer = new byte[(int) min(bytes, 8_192)];
            long skipped = 0;
            while (skipped < bytes) {
                int read = read(buffer, 0, (int) min(buffer.length, bytes - skipped));
                if (read < 0) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        @Override
        public void close()
                throws IOException
        {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                super.close();
            }
            catch (IOException failure) {
                response.streamFailed(failure);
                throw failure;
            }
            response.streamClosed();
        }
    }

    private static final class BodyCapture
    {
        private final int limit;
        private final ByteArrayOutputStream output;
        private boolean truncated;

        private BodyCapture(int limit)
        {
            this.limit = limit;
            this.output = new ByteArrayOutputStream(min(limit, 1024));
        }

        private void append(byte value)
        {
            if (output.size() < limit) {
                output.write(value);
            }
            else {
                truncated = true;
            }
        }

        private void append(byte[] bytes, int offset, int length)
        {
            int captured = min(length, limit - output.size());
            output.write(bytes, offset, captured);
            truncated |= captured < length;
        }

        private void append(ByteBuffer buffer)
        {
            int captured = min(buffer.remaining(), limit - output.size());
            if (captured > 0) {
                byte[] bytes = new byte[captured];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            truncated |= buffer.hasRemaining();
        }

        private CapturedBody snapshot(CaptureState state)
        {
            return new CapturedBody(output.toByteArray(), state, truncated);
        }
    }
}

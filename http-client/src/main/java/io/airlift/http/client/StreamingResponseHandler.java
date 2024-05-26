package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.opentelemetry.api.trace.Span;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.util.Objects.requireNonNull;

/**
 * NOTE: intended for sync client execution. Cannot be used with {@link HttpClient#executeAsync(Request, ResponseHandler)}
 */
public class StreamingResponseHandler
        implements ResponseHandler<StreamingResponseHandler.StreamingResponse, RuntimeException>
{
    private volatile Runnable completer;
    private volatile Span span;
    private volatile boolean completerHasRun;

    public static StreamingResponseHandler streamingResponseHandler()
    {
        return new StreamingResponseHandler();
    }

    private StreamingResponseHandler() {}

    public final class StreamingResponse
            implements Closeable
    {
        private final Response response;
        private final ListMultimap<HeaderName, String> headers;

        private StreamingResponse(Response response)
        {
            this.response = requireNonNull(response, "response is null");
            this.headers = ImmutableListMultimap.copyOf(response.getHeaders());
        }

        public void write(OutputStream output)
                throws IOException
        {
            response.getInputStream().transferTo(output);
            output.flush();
        }

        @Override
        public void close()
        {
            checkState(completer != null, "completer is null. The request did not end correctly.");

            completer.run();
        }

        public int statusCode()
        {
            return response.getStatusCode();
        }

        public Optional<String> header(String name)
        {
            return Optional.of(headers(name)).flatMap(values -> {
                if (values.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(values.getFirst());
            });
        }

        public List<String> headers(String name)
        {
            return headers.get(HeaderName.of(name));
        }

        public ListMultimap<HeaderName, String> headers()
        {
            return headers;
        }
    }

    @Override
    public StreamingResponse handleException(Request request, Exception exception)
            throws RuntimeException
    {
        throw propagate(request, exception);
    }

    @Override
    public StreamingResponse handle(Request request, Response response)
            throws RuntimeException
    {
        return new StreamingResponse(response);
    }

    @Override
    public void completeRequest(Runnable completer)
    {
        checkState(this.completer == null, "completeRequest has already been called");
        requireNonNull(completer, "completer is null");

        this.completer = () -> {
            try {
                completer.run();
            }
            finally {
                completerHasRun = true;

                if (span != null) {
                    span.end();
                }
            }
        };
    }

    @Override
    public void endSpan(Span span)
    {
        checkState(this.span == null, "endSpan has already been called");
        this.span = requireNonNull(span, "span is null");

        if (completerHasRun) {
            // NOTE: it is not intended that completeRequest() and endSpan() can be called from separate threads
            // the only intention here is that if completeRequest() was called prior to endSpan() we should
            // now end the span
            span.end();
        }
    }
}

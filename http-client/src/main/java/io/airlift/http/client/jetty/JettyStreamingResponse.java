package io.airlift.http.client.jetty;

import com.google.common.collect.ListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Response;
import io.airlift.http.client.StreamingResponse;
import io.opentelemetry.api.trace.Span;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

class JettyStreamingResponse
        implements StreamingResponse
{
    private final Response response;
    private final Span span;
    private final Runnable finalizer;
    private boolean isClosed;

    JettyStreamingResponse(Response response, Span span, Runnable finalizer)
    {
        this.response = requireNonNull(response, "response is null");
        this.span = requireNonNull(span, "span is null");
        this.finalizer = requireNonNull(finalizer, "finalizer is null");
    }

    @Override
    public int getStatusCode()
    {
        return response.getStatusCode();
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return response.getHeaders();
    }

    @Override
    public long getBytesRead()
    {
        return response.getBytesRead();
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return response.getInputStream();
    }

    @Override
    public void close()
    {
        if (!isClosed) {
            isClosed = true;

            try {
                finalizer.run();
            }
            finally {
                span.end();
            }
        }
    }
}

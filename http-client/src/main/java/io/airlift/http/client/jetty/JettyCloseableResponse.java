package io.airlift.http.client.jetty;

import com.google.common.collect.ListMultimap;
import io.airlift.http.client.CloseableResponse;
import io.airlift.http.client.HeaderName;
import io.opentelemetry.api.trace.Span;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

class JettyCloseableResponse
        implements CloseableResponse
{
    private final RequestController requestController;
    private final RequestContext requestContext;
    private final Span span;
    private final JettyResponse jettyResponse;
    private final long responseStart;

    JettyCloseableResponse(RequestController requestController, RequestContext requestContext, Span span, JettyResponse jettyResponse, long responseStart)
    {
        this.requestController = requireNonNull(requestController, "requestController is null");
        this.requestContext = requireNonNull(requestContext, "requestContext is null");
        this.span = requireNonNull(span, "span is null");
        this.jettyResponse = requireNonNull(jettyResponse, "jettyResponse is null");
        this.responseStart = responseStart;
    }

    @Override
    public void close()
    {
        try {
            requestController.closeResponse(jettyResponse, requestContext, span, responseStart);
        }
        finally {
            span.end();
        }
    }

    @Override
    public int getStatusCode()
    {
        return jettyResponse.getStatusCode();
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return jettyResponse.getHeaders();
    }

    @Override
    public long getBytesRead()
    {
        return jettyResponse.getBytesRead();
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return jettyResponse.getInputStream();
    }
}

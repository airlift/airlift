package io.airlift.http.client.jetty;

import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.util.InputStreamResponseListener;

import static java.util.Objects.requireNonNull;

record RequestContext(
        Request request,
        HttpRequest jettyRequest,
        InputStreamResponseListener listener,
        RequestInfo requestInfo,
        RequestSizeListener requestSize,
        long requestStart)
{
    RequestContext
    {
        requireNonNull(request, "request is null");
        requireNonNull(jettyRequest, "jettyRequest is null");
        requireNonNull(listener, "listener is null");
        requireNonNull(requestInfo, "requestInfo is null");
        requireNonNull(requestSize, "requestSize is null");
    }
}

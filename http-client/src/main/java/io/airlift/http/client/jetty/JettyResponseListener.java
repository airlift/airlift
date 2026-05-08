package io.airlift.http.client.jetty;

import io.airlift.log.Logger;
import org.eclipse.jetty.client.AbstractResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.RetainableByteBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;

class JettyResponseListener<T, E extends Exception>
        extends AbstractResponseListener
{
    private static final Logger log = Logger.get(JettyResponseListener.class);

    private final Request request;
    private final JettyResponseFuture<T, E> future;

    public JettyResponseListener(ByteBufferPool.Sized bufferPool, Request request, JettyResponseFuture<T, E> future, int maxLength)
    {
        super(new RetainableByteBuffer.DynamicCapacity(requireNonNull(bufferPool, "bufferPool is null"), maxLength, 0));
        this.future = requireNonNull(future, "future is null");
        this.request = requireNonNull(request, "request is null");
    }

    public JettyResponseFuture<T, E> send()
    {
        request.send(this);
        return future;
    }

    @Override
    public void onComplete(Result result)
    {
        // Request-side-only failures surface differently on HTTP/1.1 vs stream-based protocols.
        Response response = result.getResponse();
        if (response != null && isStreamBased(response.getVersion())) {
            completeStreamBased(result, response);
        }
        else {
            completeHttp1(result, response);
        }
    }

    private static boolean isStreamBased(HttpVersion version)
    {
        return switch (version) {
            case null -> false;
            case HTTP_2, HTTP_3 -> true;
            case HTTP_0_9, HTTP_1_0, HTTP_1_1 -> false;
        };
    }

    private void completeHttp1(Result result, Response response)
    {
        Throwable responseFailure = result.getResponseFailure();
        if (responseFailure != null) {
            future.failed(responseFailure);
            return;
        }
        if (response == null) {
            Throwable requestFailure = result.getRequestFailure();
            if (requestFailure == null) {
                // Settle the future so the caller is not left blocking on a violated invariant.
                future.failed(new IllegalStateException("Result has neither response nor failure: " + result));
                return;
            }
            future.failed(requestFailure);
            return;
        }
        Throwable requestFailure = result.getRequestFailure();
        if (requestFailure != null) {
            log.debug(requestFailure, "Suppressing request failure for fully-received response from %s", request.getURI());
        }
        deliver(response);
    }

    private void completeStreamBased(Result result, Response response)
    {
        Throwable responseFailure = result.getResponseFailure();
        if (responseFailure == null) {
            deliver(response);
            return;
        }
        // Transport-layer failure after headers were committed (e.g. a server stream reset
        // following an early response). Locally-initiated aborts propagate as RuntimeException.
        if (response.getStatus() > 0 && responseFailure instanceof IOException) {
            log.debug(responseFailure, "Suppressing transport failure after headers were received from %s", request.getURI());
            deliver(response);
            return;
        }
        future.failed(responseFailure);
    }

    private void deliver(Response response)
    {
        try (InputStream stream = takeContentAsInputStream()) {
            future.completed(response, stream);
        }
        catch (IOException e) {
            future.failed(new UncheckedIOException("Failed communicating with server: " + request.getURI().toASCIIString(), e));
        }
    }
}

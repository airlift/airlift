package io.airlift.http.client.jetty;

import io.airlift.log.Logger;
import org.eclipse.jetty.client.AbstractResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
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
        // A response failure is only recorded while the response is still pending, so a
        // non-null failure means the body was not fully received (truncation, stream reset
        // mid-body, GOAWAY, connection EOF) and Jetty has already discarded the accumulated
        // content. A request-side failure with a complete response (an upload aborted after
        // the server answered early, on any protocol) still delivers the response.
        Throwable responseFailure = result.getResponseFailure();
        if (responseFailure != null) {
            future.failed(responseFailure);
            return;
        }
        Response response = result.getResponse();
        Throwable requestFailure = result.getRequestFailure();
        if (response == null) {
            if (requestFailure == null) {
                // Settle the future so the caller is not left blocking on a violated invariant.
                future.failed(new IllegalStateException("Result has neither response nor failure: " + result));
                return;
            }
            future.failed(requestFailure);
            return;
        }
        if (requestFailure != null) {
            log.debug(requestFailure, "Suppressing request failure for fully-received response from %s", request.getURI());
        }
        deliver(response);
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

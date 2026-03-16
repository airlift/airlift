package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.AbstractResponseListener;
import org.eclipse.jetty.client.Request;
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
        if (result.isFailed()) {
            future.failed(result.getFailure());
        }
        else {
            try (InputStream stream = takeContentAsInputStream()) {
                future.completed(result.getResponse(), stream);
            }
            catch (IOException e) {
                future.failed(new UncheckedIOException("Failed communicating with server: " + result.getRequest().getURI().toASCIIString(), e));
            }
        }
    }
}

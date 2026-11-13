package io.airlift.http.client.jetty;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.http.client.AsyncBodyGenerator;
import org.eclipse.jetty.client.AsyncRequestContent;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Delivers the content of an {@link AsyncBodyGenerator} to a Jetty request.
 */
class JettyAsyncRequestBody
        implements AsyncBodyGenerator.Sink
{
    private final AsyncBodyGenerator generator;
    private final HttpRequest jettyRequest;
    private final AsyncRequestContent content;
    // writes whose content was not reported as written to the connection yet
    private final Set<OutstandingWrite> outstandingWrites = ConcurrentHashMap.newKeySet();

    JettyAsyncRequestBody(AsyncBodyGenerator generator, HttpRequest jettyRequest)
    {
        this.generator = requireNonNull(generator, "generator is null");
        this.jettyRequest = requireNonNull(jettyRequest, "jettyRequest is null");
        this.content = new AsyncRequestContent(generator.contentType());

        jettyRequest.body(content);
        // Jetty releases the chunk it is sending when the request fails, which reports it as written.
        // The writes are failed first, by the listener that is notified before that happens.
        jettyRequest.onRequestFailure((_, failure) -> failWrites(failure));
        // However the request ends, content that was not sent must not wait forever
        jettyRequest.onComplete(result -> {
            Throwable failure = result.getFailure();
            if (failure == null) {
                failure = new IOException("Request is complete");
            }
            failWrites(failure);
            content.fail(failure);
        });
    }

    /**
     * Starts delivering the body. Called when the request is about to be sent, so a body of a request
     * that is not sent is left to the caller.
     */
    void attach()
    {
        generator.attach(this);
    }

    @Override
    public ListenableFuture<Void> write(ByteBuffer buffer)
    {
        OutstandingWrite write = new OutstandingWrite();
        outstandingWrites.add(write);
        // the callback completes when the buffer was written to the connection, or the request failed
        content.write(buffer, new Callback()
        {
            @Override
            public void succeeded()
            {
                outstandingWrites.remove(write);
                write.future.set(null);
            }

            @Override
            public void failed(Throwable failure)
            {
                outstandingWrites.remove(write);
                write.future.setException(failure);
            }
        });
        return write.future;
    }

    @Override
    public void close()
    {
        content.close();
    }

    @Override
    public void fail(Throwable failure)
    {
        failWrites(failure);
        content.fail(failure);
        // Content that was sent completely cannot be failed any more,
        // while the request may still be waiting for its response
        jettyRequest.abort(failure);
    }

    /**
     * Fails the writes that were not completed, including the chunk that Jetty holds while sending,
     * which {@link AsyncRequestContent#fail} cannot reach and would release as written.
     */
    private void failWrites(Throwable failure)
    {
        for (OutstandingWrite write : outstandingWrites) {
            if (outstandingWrites.remove(write)) {
                write.future.setException(failure);
            }
        }
    }

    // compared by identity, which futures do not define
    private static class OutstandingWrite
    {
        private final SettableFuture<Void> future = SettableFuture.create();
    }
}

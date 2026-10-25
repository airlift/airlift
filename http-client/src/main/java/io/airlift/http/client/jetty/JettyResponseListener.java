package io.airlift.http.client.jetty;

import io.airlift.http.client.StreamingResponseHandler.BodyConsumer;
import io.airlift.log.Logger;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;

import java.nio.ByteBuffer;

import static io.airlift.http.client.ResponseHandlerUtils.sanitizeUri;
import static java.util.Objects.requireNonNull;

/**
 * Passes each response chunk to the body consumer of the response handler before demanding the next one.
 */
class JettyResponseListener<T, E extends Exception>
        implements Response.Listener
{
    private static final Logger log = Logger.get(JettyResponseListener.class);

    private final Request request;
    private final JettyResponseFuture<T, E> future;
    private final int maxLength;

    private BodyConsumer<T, E> consumer;
    private long bytesReceived;
    private Throwable handlerFailure;

    public JettyResponseListener(Request request, JettyResponseFuture<T, E> future, int maxLength)
    {
        this.future = requireNonNull(future, "future is null");
        this.request = requireNonNull(request, "request is null");
        this.maxLength = maxLength;
    }

    public JettyResponseFuture<T, E> send()
    {
        request.send(this);
        return future;
    }

    @Override
    public void onHeaders(Response response)
    {
        long contentLength = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH);
        if (HttpMethod.HEAD.is(response.getRequest().getMethod()) || bodyIsOmitted(response.getStatus())) {
            contentLength = 0;
        }
        if (contentLength > maxLength) {
            response.abort(bufferingCapacityExceeded());
            return;
        }
        try {
            consumer = future.headersReceived(response);
        }
        catch (Throwable e) {
            abortWithHandlerFailure(response, e);
        }
    }

    private static boolean bodyIsOmitted(int status)
    {
        return status == HttpStatus.NO_CONTENT_204 || status == HttpStatus.NOT_MODIFIED_304;
    }

    @Override
    public void onContent(Response response, Content.Chunk chunk, Runnable demander)
    {
        ByteBuffer buffer = chunk.getByteBuffer();
        int remaining = buffer.remaining();
        if (bytesReceived + remaining > maxLength) {
            response.abort(bufferingCapacityExceeded());
            return;
        }
        bytesReceived += remaining;
        try {
            consumer.accept(buffer);
        }
        catch (Throwable e) {
            abortWithHandlerFailure(response, e);
            return;
        }
        demander.run();
    }

    private IllegalArgumentException bufferingCapacityExceeded()
    {
        return new IllegalArgumentException("Buffering capacity " + maxLength + " exceeded");
    }

    private void abortWithHandlerFailure(Response response, Throwable failure)
    {
        handlerFailure = failure;
        consumer = null;
        response.abort(failure);
    }

    @Override
    public void onFailure(Response response, Throwable failure)
    {
        consumer = null;
    }

    @Override
    public void onComplete(Result result)
    {
        // A failure thrown by the response handler is not passed back to its handleException
        if (handlerFailure != null) {
            future.handlerFailed(result.getResponse(), handlerFailure, bytesReceived);
            return;
        }
        // A response failure is only recorded while the response is still pending, so a
        // non-null failure means the body was not fully received (truncation, stream reset
        // mid-body, GOAWAY, connection EOF) and this listener has discarded the accumulated
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
            log.debug(requestFailure, "Suppressing request failure for fully-received response from %s", sanitizeUri(request.getURI()));
        }
        BodyConsumer<T, E> completedConsumer = consumer;
        consumer = null;
        future.completed(response, completedConsumer, bytesReceived);
    }
}

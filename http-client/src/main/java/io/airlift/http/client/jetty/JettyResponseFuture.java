package io.airlift.http.client.jetty;

import com.google.common.util.concurrent.AbstractFuture;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.eclipse.jetty.client.Response;

import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

class JettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements HttpClient.HttpResponseFuture<T>
{
    private enum JettyAsyncHttpState
    {
        WAITING_FOR_CONNECTION,
        PROCESSING_RESPONSE,
        DONE,
        FAILED,
        CANCELED
    }

    private final long requestStart = System.nanoTime();
    private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
    private final Request request;
    private final org.eclipse.jetty.client.Request jettyRequest;
    private final LongSupplier requestSize;
    private final ResponseHandler<T, E> responseHandler;
    private final Span span;
    private final RequestStats stats;
    private final boolean recordRequestComplete;

    JettyResponseFuture(
            Request request,
            org.eclipse.jetty.client.Request jettyRequest,
            LongSupplier requestSize,
            ResponseHandler<T, E> responseHandler,
            Span span,
            RequestStats stats,
            boolean recordRequestComplete)
    {
        this.request = requireNonNull(request, "request is null");
        this.jettyRequest = requireNonNull(jettyRequest, "jettyRequest is null");
        this.requestSize = requireNonNull(requestSize, "requestSize is null");
        this.responseHandler = requireNonNull(responseHandler, "responseHandler is null");
        this.span = requireNonNull(span, "span is null");
        this.stats = requireNonNull(stats, "stats is null");
        this.recordRequestComplete = recordRequestComplete;
    }

    @Override
    public String getState()
    {
        return state.get().toString();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        try {
            span.setStatus(StatusCode.ERROR, "cancelled");
            stats.recordRequestCanceled();
            state.set(JettyAsyncHttpState.CANCELED);
            jettyRequest.abort(new CancellationException());
            return super.cancel(mayInterruptIfRunning);
        }
        catch (Throwable e) {
            setException(e);
            return true;
        }
        finally {
            span.end();
        }
    }

    void completed(Response response, InputStream content)
    {
        if (state.get() == JettyAsyncHttpState.CANCELED) {
            return;
        }

        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, response.getStatus());

        if (request.getBodyGenerator() != null) {
            span.setAttribute(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestSize.getAsLong());
        }

        T value;
        try {
            value = processResponse(response, content);
        }
        catch (Throwable e) {
            // this will be an instance of E from the response handler or an Error
            storeException(e);
            return;
        }
        state.set(JettyAsyncHttpState.DONE);
        set(value);

        span.setStatus(StatusCode.OK);
        span.end();
    }

    private T processResponse(Response response, InputStream content)
            throws E
    {
        // this time will not include the data fetching portion of the response,
        // since the response is fully cached in memory at this point
        long responseStart = System.nanoTime();

        state.set(JettyAsyncHttpState.PROCESSING_RESPONSE);
        JettyResponse jettyResponse = null;
        T value;
        try {
            jettyResponse = new JettyResponse(response, content);
            value = responseHandler.handle(request, jettyResponse);
        }
        finally {
            if (jettyResponse != null) {
                span.setAttribute(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, jettyResponse.getBytesRead());
            }
            if (recordRequestComplete) {
                JettyHttpClient.recordRequestComplete(stats, request, requestSize.getAsLong(), requestStart, jettyResponse, responseStart);
            }
        }
        return value;
    }

    void failed(Throwable throwable)
    {
        if (state.get() == JettyAsyncHttpState.CANCELED) {
            return;
        }

        stats.recordRequestFailed();

        // give handler a chance to rewrite the exception or return a value instead
        if (throwable instanceof Exception) {
            try {
                T value = responseHandler.handleException(request, (Exception) throwable);
                // handler returned a value, store it in the future
                state.set(JettyAsyncHttpState.DONE);
                set(value);
                return;
            }
            catch (Throwable newThrowable) {
                throwable = newThrowable;
            }
        }

        // at this point "throwable" will either be an instance of E
        // from the response handler or not an instance of Exception
        storeException(throwable);
    }

    private void storeException(Throwable throwable)
    {
        if (throwable instanceof CancellationException) {
            state.set(JettyAsyncHttpState.CANCELED);
        }
        else {
            state.set(JettyAsyncHttpState.FAILED);
        }

        if (throwable == null) {
            throwable = new Throwable("Throwable is null");
        }

        setException(throwable);

        span.setStatus(StatusCode.ERROR, throwable.getMessage());
        span.recordException(throwable, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true));
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("requestStart", requestStart)
                .add("state", state)
                .add("request", request)
                .toString();
    }
}

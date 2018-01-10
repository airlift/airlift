package io.airlift.http.client.jetty;

import com.google.common.util.concurrent.AbstractFuture;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.log.Logger;
import org.eclipse.jetty.client.api.Response;

import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.MoreObjects.toStringHelper;

class JettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements HttpClient.HttpResponseFuture<T>
{
    public enum JettyAsyncHttpState
    {
        WAITING_FOR_CONNECTION,
        SENDING_REQUEST,
        WAITING_FOR_RESPONSE,
        PROCESSING_RESPONSE,
        DONE,
        FAILED,
        CANCELED
    }

    private static final Logger log = Logger.get(JettyResponseFuture.class);

    private final long requestStart = System.nanoTime();
    private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
    private final Request request;
    private final org.eclipse.jetty.client.api.Request jettyRequest;
    private final ResponseHandler<T, E> responseHandler;
    private final RequestStats stats;
    private final boolean recordRequestComplete;

    public JettyResponseFuture(Request request, org.eclipse.jetty.client.api.Request jettyRequest, ResponseHandler<T, E> responseHandler, RequestStats stats, boolean recordRequestComplete)
    {
        this.request = request;
        this.jettyRequest = jettyRequest;
        this.responseHandler = responseHandler;
        this.stats = stats;
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
            stats.recordRequestCanceled();
            state.set(JettyAsyncHttpState.CANCELED);
            jettyRequest.abort(new CancellationException());
            return super.cancel(mayInterruptIfRunning);
        }
        catch (Throwable e) {
            setException(e);
            return true;
        }
    }

    protected void completed(Response response, InputStream content)
    {
        if (state.get() == JettyAsyncHttpState.CANCELED) {
            return;
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
            if (recordRequestComplete) {
                JettyHttpClient.recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
            }
        }
        return value;
    }

    protected void failed(Throwable throwable)
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
            log.error(throwable, "Something is broken");
        }

        setException(throwable);
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

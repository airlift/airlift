package com.proofpoint.http.client.netty;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.AbstractFuture;
import com.proofpoint.http.client.AsyncHttpClient.AsyncHttpResponseFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import org.jboss.netty.channel.ConnectTimeoutException;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class NettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements AsyncHttpResponseFuture<T>
{
    public enum NettyAsyncHttpState
    {
        WAITING_FOR_CONNECTION,
        SENDING_REQUEST,
        WAITING_FOR_RESPONSE,
        PROCESSING_RESPONSE,
        DONE,
        FAILED,
        CANCELED
    }

    private static final Logger log = Logger.get(NettyResponseFuture.class);

    private final long requestStart = System.nanoTime();
    private final AtomicReference<NettyAsyncHttpState> state = new AtomicReference<>(NettyAsyncHttpState.WAITING_FOR_CONNECTION);
    private final Request request;
    private final ResponseHandler<T, E> responseHandler;
    private final RequestStats stats;


    public NettyResponseFuture(Request request, ResponseHandler<T, E> responseHandler, RequestStats stats)
    {
        this.request = request;
        this.responseHandler = responseHandler;
        this.stats = stats;
    }

    public String getState()
    {
        return state.get().toString();
    }

    protected void setState(NettyAsyncHttpState state)
    {
        this.state.set(state);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        // Currently, we do not cancel pending requests
        state.set(NettyAsyncHttpState.CANCELED);
        return super.cancel(mayInterruptIfRunning);
    }

    protected void completed(HttpResponse httpResponse)
    {
        if (state.get() == NettyAsyncHttpState.CANCELED) {
            return;
        }

        T value;
        try {
            value = processResponse(httpResponse);
        }
        catch (Throwable e) {
            // this will be an instance of E from the response handler or an Error
            storeException(e);
            return;
        }
        state.set(NettyAsyncHttpState.DONE);
        set(value);
    }

    private T processResponse(HttpResponse httpResponse)
            throws E
    {
        // this time will not include the data fetching portion of the response,
        // since the response is fully cached in memory at this point
        long responseStart = System.nanoTime();

        state.set(NettyAsyncHttpState.PROCESSING_RESPONSE);
        NettyResponse response = null;
        T value;
        try {
            response = new NettyResponse(httpResponse);
            value = responseHandler.handle(request, response);
        }
        finally {
            Duration responseProcessingTime = Duration.nanosSince(responseStart);
            Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

            if (response != null) {
                stats.record(request.getMethod(),
                        response.getStatusCode(),
                        response.getBytesRead(),
                        response.getBytesRead(),
                        requestProcessingTime,
                        responseProcessingTime);
            }
        }
        return value;
    }

    protected void failed(Throwable throwable)
    {
        if (state.get() == NettyAsyncHttpState.CANCELED) {
            return;
        }

        if (throwable instanceof ConnectTimeoutException) {
            throwable = new SocketTimeoutException(throwable.getMessage());
        }

        // give handler a chance to rewrite the exception or return a value instead
        if (throwable instanceof Exception) {
            try {
                T value = responseHandler.handleException(request, (Exception) throwable);
                // handler returned a value, store it in the future
                state.set(NettyAsyncHttpState.DONE);
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
            state.set(NettyAsyncHttpState.CANCELED);
        }
        else {
            state.set(NettyAsyncHttpState.FAILED);
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
        return Objects.toStringHelper(this)
                .add("requestStart", requestStart)
                .add("state", state)
                .add("request", request)
                .toString();
    }
}

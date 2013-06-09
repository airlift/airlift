package io.airlift.http.client.netty;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractFuture;
import io.airlift.http.client.AsyncHttpClient.AsyncHttpResponseFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class NettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements AsyncHttpResponseFuture<T, E>
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

    private static final Logger log = Logger.get(NettyResponseFuture.class);

    @Override
    protected boolean setException(Throwable throwable)
    {
        if (state.get() == NettyAsyncHttpState.CANCELED) {
            return false;
        }

        if (throwable instanceof CancellationException) {
            state.set(NettyAsyncHttpState.CANCELED);
        } else {
            state.set(NettyAsyncHttpState.FAILED);
        }
        if (throwable == null) {
            throwable = new Throwable("Throwable is null");
            log.error(throwable, "Something is broken");
        }

        return super.setException(throwable);
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

        // this time will not include the data fetching portion of the response,
        // since the response is fully cached in memory at this point
        long responseStart = System.nanoTime();

        state.set(NettyAsyncHttpState.PROCESSING_RESPONSE);
        NettyResponse response = null;
        try {
            response = new NettyResponse(httpResponse);
            T value = responseHandler.handle(request, response);
            set(value);
            state.set(NettyAsyncHttpState.DONE);
        }
        catch (Exception e) {
            setException(new ExceptionFromResponseHandler(e));
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
    }

    @Override
    public T checkedGet()
            throws E
    {
        try {
            return get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return mapException(e);
        }
        catch (CancellationException | ExecutionException e) {
            return mapException(e);
        }
    }

    @Override
    public T checkedGet(long timeout, TimeUnit unit)
            throws TimeoutException, E
    {
        try {
            return get(timeout, unit);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return mapException(e);
        }
        catch (CancellationException | ExecutionException e) {
            return mapException(e);
        }
    }

    private T mapException(Exception e) throws E
    {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        if (e instanceof ExecutionException) {
            Throwable cause = e.getCause();
            // Do not ask the handler to "handle" an exception it produced
            if (cause instanceof ExceptionFromResponseHandler) {
                try {
                    throw (E) cause.getCause();
                }
                catch (ClassCastException classCastException) {
                    // this should never happen but generics suck so be safe
                    // handler will be notified of the same exception again below
                }
            }
            if (cause instanceof Exception) {
                e = (Exception) cause;
            }
        }
        return responseHandler.handleException(request, e);
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

    private static class ExceptionFromResponseHandler
            extends Exception
    {
        private ExceptionFromResponseHandler(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }
}

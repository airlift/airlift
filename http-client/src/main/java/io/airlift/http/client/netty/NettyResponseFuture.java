package io.airlift.http.client.netty;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.CheckedFuture;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.units.Duration;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements CheckedFuture<T, E>
{
    private final long requestStart = System.nanoTime();
    private final Request request;
    private final ResponseHandler<T, E> responseHandler;
    private final RequestStats stats;

    private final AtomicBoolean canceled = new AtomicBoolean();

    public NettyResponseFuture(Request request, ResponseHandler<T, E> responseHandler, RequestStats stats)
    {
        this.request = request;
        this.responseHandler = responseHandler;
        this.stats = stats;
    }

    @Override
    protected boolean setException(Throwable throwable)
    {
        return !canceled.get() && super.setException(throwable);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        // Currently, we do not cancel pending requests
        canceled.set(true);
        return super.cancel(mayInterruptIfRunning);
    }

    protected void completed(HttpResponse httpResponse)
    {
        if (canceled.get()) {
            return;
        }

        // this time will not include the data fetching portion of the response,
        // since the response is fully cached in memory at this point
        long responseStart = System.nanoTime();

        NettyResponse response = new NettyResponse(httpResponse);
        try {
            T value = responseHandler.handle(request, response);
            set(value);
        }
        catch (Exception e) {
            setException(new ExceptionFromResponseHandler(e));
        }
        finally {
            Duration responseProcessingTime = Duration.nanosSince(responseStart);
            Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

            stats.record(request.getMethod(),
                    response.getStatusCode(),
                    response.getBytesRead(),
                    response.getBytesRead(),
                    requestProcessingTime,
                    responseProcessingTime);
        }
    }

    @Override
    public T checkedGet()
            throws E
    {
        try {
            return get();
        }
        catch (InterruptedException | CancellationException | ExecutionException e) {
            throw mapException(e);
        }
    }

    @Override
    public T checkedGet(long timeout, TimeUnit unit)
            throws TimeoutException, E
    {
        try {
            return get(timeout, unit);
        }
        catch (InterruptedException | CancellationException | ExecutionException e) {
            throw mapException(e);
        }
    }

    private E mapException(Exception e)
    {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        if (e instanceof ExecutionException) {
            Throwable cause = e.getCause();
            // Do not ask the handler to "handle" an exception it produced
            if (cause instanceof ExceptionFromResponseHandler) {
                try {
                    return (E) cause.getCause();
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

    private static class ExceptionFromResponseHandler
            extends Exception
    {
        private ExceptionFromResponseHandler(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }
}

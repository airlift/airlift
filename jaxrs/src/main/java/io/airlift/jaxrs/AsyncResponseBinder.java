package io.airlift.jaxrs;

import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.units.Duration;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.airlift.jaxrs.AsyncResponseHandler.bindAsyncResponse;
import static jakarta.ws.rs.core.Response.status;
import static java.util.Objects.requireNonNull;

public class AsyncResponseBinder
{
    private final HttpServletRequest servletRequest;
    private boolean cancelOnClientDisconnect = true;
    private Optional<Duration> timeout = Optional.empty();
    private Function<Duration, Supplier<Response>> timeoutResponse = AsyncResponseBinder::defaultTimeoutResponse;

    private AsyncResponseBinder(HttpServletRequest servletRequest)
    {
        this.servletRequest = requireNonNull(servletRequest, "servletRequest is null");
    }

    public AsyncResponseBinder cancelOnClientDisconnection(boolean cancelOnClientDisconnection)
    {
        this.cancelOnClientDisconnect = cancelOnClientDisconnection;
        return this;
    }

    public AsyncResponseBinder withTimeout(Duration timeout)
    {
        this.timeout = Optional.of(timeout);
        return this;
    }

    public AsyncResponseBinder withTimeoutResponse(Response response)
    {
        requireNonNull(response, "response is null");
        this.timeoutResponse = timeout -> () -> response;
        return this;
    }

    public AsyncResponseBinder withTimeoutResponse(Supplier<Response> responseSupplier)
    {
        requireNonNull(responseSupplier, "responseSupplier is null");
        this.timeoutResponse = timeout -> responseSupplier;
        return this;
    }

    public void bindTo(AsyncResponse response, ListenableFuture<?> future, Executor httpExecutor)
    {
        AsyncResponseHandler<?> asyncResponseHandler = bindAsyncResponse(response, future, httpExecutor);
        if (cancelOnClientDisconnect) {
            servletRequest.getAsyncContext().addListener(new AsyncListener()
            {
                @Override
                public void onError(AsyncEvent event)
                {
                    // Jetty will signal the client disconnection with the EofException
                    if (event.getThrowable().getMessage().contains("cancel_stream_error")) {
                        asyncResponseHandler.clientDisconnected();
                    }
                }

                @Override
                public void onComplete(AsyncEvent event) {}

                @Override
                public void onTimeout(AsyncEvent event) {}

                @Override
                public void onStartAsync(AsyncEvent event) {}
            });
        }
        timeout.ifPresent(timeout -> asyncResponseHandler.withTimeout(timeout, timeoutResponse.apply(timeout)));
    }

    private static Supplier<Response> defaultTimeoutResponse(Duration timeout)
    {
        return () -> status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("Timed out after waiting for " + timeout.convertToMostSuccinctTimeUnit())
                .build();
    }

    public static class Factory
            implements Supplier<AsyncResponseBinder>
    {
        private final HttpServletRequest servletRequest;

        private Factory(@Context HttpServletRequest servletRequest)
        {
            this.servletRequest = requireNonNull(servletRequest, "servletRequest is null");
        }

        @Override
        public AsyncResponseBinder get()
        {
            return new AsyncResponseBinder(servletRequest);
        }
    }
}

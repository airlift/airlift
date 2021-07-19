package io.airlift.http.client.testing;

import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseListener;
import io.airlift.units.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class TestingHttpClient
        implements HttpClient
{
    private final Processor processor;
    private final ListeningExecutorService executor;

    private final RequestStats stats = new RequestStats();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TestingHttpClient(Processor processor)
    {
        this(processor, newDirectExecutorService());
    }

    public TestingHttpClient(Processor processor, ExecutorService executor)
    {
        this.processor = requireNonNull(processor, "processor is null");
        this.executor = listeningDecorator(requireNonNull(executor, "executor is null"));
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler, ResponseListener responseListener)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");

        AtomicReference<String> state = new AtomicReference<>("SENDING_REQUEST");
        ListenableFuture<T> future = executor.submit(() -> execute(request, responseHandler, state));

        return new TestingHttpResponseFuture<>(future, state);
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");
        return execute(request, responseHandler, new AtomicReference<>("SENDING_REQUEST"));
    }

    private <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler, AtomicReference<String> state)
            throws E
    {
        state.set("PROCESSING_REQUEST");
        Response response;
        long requestStart = System.nanoTime();
        try {
            response = processor.handle(request);
        }
        catch (Exception | Error e) {
            state.set("FAILED");
            long responseStart = System.nanoTime();
            Duration requestProcessingTime = new Duration(responseStart - requestStart, NANOSECONDS);
            if (e instanceof Exception) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                try {
                    return responseHandler.handleException(request, (Exception) e);
                }
                finally {
                    stats.recordResponseReceived(request.getMethod(),
                            0,
                            0,
                            0,
                            requestProcessingTime,
                            Duration.nanosSince(responseStart));
                }
            }
            stats.recordResponseReceived(request.getMethod(),
                    0,
                    0,
                    0,
                    requestProcessingTime,
                    new Duration(0, NANOSECONDS));
            throw (Error) e;
        }
        checkState(response != null, "response is null");

        // notify handler
        state.set("PROCESSING_RESPONSE");
        long responseStart = System.nanoTime();
        Duration requestProcessingTime = new Duration(responseStart - requestStart, NANOSECONDS);
        try {
            return responseHandler.handle(request, response);
        }
        finally {
            state.set("DONE");
            stats.recordResponseReceived(request.getMethod(),
                    response.getStatusCode(),
                    response.getBytesRead(),
                    response.getBytesRead(),
                    requestProcessingTime,
                    Duration.nanosSince(responseStart));
        }
    }

    @Override
    public RequestStats getStats()
    {
        return stats;
    }

    @Override
    public long getMaxContentLength()
    {
        return new HttpClientConfig().getMaxContentLength().toBytes();
    }

    @Override
    public void close()
    {
        closed.set(true);
    }

    @Override
    public boolean isClosed()
    {
        return closed.get();
    }

    public interface Processor
    {
        Response handle(Request request)
                throws Exception;
    }

    private class TestingHttpResponseFuture<T>
            extends ForwardingListenableFuture<T>
            implements HttpResponseFuture<T>
    {
        private final AtomicReference<String> state;
        private final ListenableFuture<T> future;

        private TestingHttpResponseFuture(ListenableFuture<T> future, AtomicReference<String> state)
        {
            this.future = future;
            this.state = state;
        }

        @Override
        protected ListenableFuture<T> delegate()
        {
            return future;
        }

        @Override
        public String getState()
        {
            return state.get();
        }
    }
}

package com.proofpoint.http.client.testing;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class TestingHttpClient
        implements AsyncHttpClient
{
    private final Processor processor;
    private final ListeningExecutorService executor;

    private final RequestStats stats = new RequestStats();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TestingHttpClient(Function<Request, Response> processor)
    {
        this(processor, MoreExecutors.sameThreadExecutor());
    }

    public TestingHttpClient(Processor processor)
    {
        this(processor, MoreExecutors.sameThreadExecutor());
    }

    public TestingHttpClient(final Function<Request, Response> processor, ExecutorService executor)
    {
        this(new Processor()
        {
            @Override
            public Response handle(Request request)
            {
                return processor.apply(request);
            }
        }, executor);
    }

    public TestingHttpClient(Processor processor, ExecutorService executor)
    {
        this.processor = processor;
        this.executor = MoreExecutors.listeningDecorator(executor);
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(final Request request, final ResponseHandler<T, E> responseHandler)
    {
        checkNotNull(request, "request is null");
        checkNotNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");

        final AtomicReference<String> state = new AtomicReference<>("SENDING_REQUEST");
        ListenableFuture<T> future = executor.submit(new Callable<T>()
        {
            @Override
            public T call()
                    throws Exception
            {
                return execute(request, responseHandler, state);
            }
        });

        return new TestingHttpResponseFuture(future, state);
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        checkNotNull(request, "request is null");
        checkNotNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");
        return execute(request, responseHandler, new AtomicReference<>("SENDING_REQUEST"));
    }

    private <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler, AtomicReference<String> state)
            throws E
    {
        state.set("PROCESSING_REQUEST");
        long requestStart = System.nanoTime();
        Response response;
        try {
            response = processor.handle(request);
        }
        catch (Throwable e) {
            state.set("FAILED");
            long responseStart = System.nanoTime();
            Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);
            if (e instanceof Exception) {
                try {
                    return responseHandler.handleException(request, (Exception) e);
                }
                finally {
                    stats.record(request.getMethod(),
                            0,
                            0,
                            0,
                            requestProcessingTime,
                            Duration.nanosSince(responseStart));
                }
            }
            else {
                stats.record(request.getMethod(),
                        0,
                        0,
                        0,
                        requestProcessingTime,
                        new Duration(0, TimeUnit.NANOSECONDS));
                throw (Error) e;
            }
        }
        checkState(response != null, "response is null");

        // notify handler
        state.set("PROCESSING_RESPONSE");
        long responseStart = System.nanoTime();
        Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);
        try {
            return responseHandler.handle(request, response);
        }
        finally {
            state.set("DONE");
            stats.record(request.getMethod(),
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
    public void close()
    {
        closed.set(true);
    }

    public interface Processor
    {
        @NotNull
        Response handle(Request request)
                throws Exception;
    }

    private static class TestingHttpResponseFuture<T>
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
        public ListenableFuture<T> delegate()
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

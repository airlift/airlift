package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractCheckedFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Beta
public class AsyncHttpClient
{
    private final HttpClient httpClient;
    private final ListeningExecutorService executor;

    public AsyncHttpClient(HttpClient httpClient, ExecutorService executor)
    {
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(executor, "executor is null");

        this.httpClient = httpClient;
        this.executor = MoreExecutors.listeningDecorator(executor);
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    public <T, E extends Exception> CheckedFuture<T, E> execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        ListenableFuture<T> listenableFuture = executor.submit(new HttpExecution<T>(request, responseHandler));
        return new ResponseFuture<T, E>(request, responseHandler, listenableFuture);
    }

    private class HttpExecution<T> implements Callable<T>
    {
        private final Request request;
        private final ResponseHandler<T, ?> responseHandler;

        public HttpExecution(Request request, ResponseHandler<T, ?> responseHandler)
        {
            this.request = request;
            this.responseHandler = responseHandler;
        }

        public T call()
                throws Exception
        {
            try {
                return httpClient.execute(request, responseHandler);
            }
            catch (Exception e) {
                throw new ExceptionFromHttpClient(e);
            }
        }
    }

    private static class ResponseFuture<T, E extends Exception> extends AbstractCheckedFuture<T, E>
    {
        private final Request request;
        private final ResponseHandler<T, E> responseHandler;

        private ResponseFuture(Request request, ResponseHandler<T, E> responseHandler, ListenableFuture<T> delegate)
        {
            super(delegate);
            this.request = request;
            this.responseHandler = responseHandler;
        }

        @Override
        protected E mapException(Exception e)
        {
            if (e instanceof ExecutionException) {
                Throwable cause = e.getCause();
                // Do not ask the handler to "handle" an exception it produced
                if (cause instanceof ExceptionFromHttpClient) {
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
    }

    private static class ExceptionFromHttpClient extends Exception
    {
        private ExceptionFromHttpClient(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }
}

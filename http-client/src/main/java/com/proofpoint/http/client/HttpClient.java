package com.proofpoint.http.client;

import com.google.common.base.Preconditions;
import com.google.common.io.Closeables;
import com.google.common.io.CountingOutputStream;
import com.google.common.util.concurrent.AbstractCheckedFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class HttpClient
{
    private final ListeningExecutorService executor;
    private final RequestStats stats = new RequestStats();
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public HttpClient(ExecutorService executor, HttpClientConfig config)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(config.getConnectTimeout(), "config.getConnectTimeout() is null");
        Preconditions.checkNotNull(config.getReadTimeout(), "config.getReadTimeout() is null");

        this.executor = MoreExecutors.listeningDecorator(executor);

        connectTimeout = config.getConnectTimeout();
        readTimeout = config.getReadTimeout();
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
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
        private final long created = System.nanoTime();


        public HttpExecution(Request request, ResponseHandler<T, ?> responseHandler)
        {
            this.request = request;
            this.responseHandler = responseHandler;
        }

        public T call()
                throws Exception
        {
            Duration schedulingDelay = Duration.nanosSince(created);
            long requestStart = System.nanoTime();

            CountingOutputStream outputStream = null;
            Response response = null;
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) request.getUri().toURL().openConnection(Proxy.NO_PROXY);

                urlConnection.setConnectTimeout((int) connectTimeout.toMillis());
                urlConnection.setReadTimeout((int) readTimeout.toMillis());

                urlConnection.setRequestMethod(request.getMethod());
                for (Entry<String, String> entry : request.getHeaders().entries()) {
                    urlConnection.addRequestProperty(entry.getKey(), entry.getValue());
                }

                if (request.getBodyGenerator() != null) {
                    urlConnection.setDoOutput(true);
                    urlConnection.setChunkedStreamingMode(4096);
                    outputStream = new CountingOutputStream(urlConnection.getOutputStream());
                    request.getBodyGenerator().write(outputStream);
                    outputStream.close();
                }

                // Get the response
                response = new Response(urlConnection);
                Duration requestProcessingTime = Duration.nanosSince(requestStart);
                long responseStart = System.nanoTime();
                try {
                    return responseHandler.handle(request, response);
                }
                catch (Exception e) {
                    throw new ExceptionFromResponseHandler(e);
                } finally {
                    Duration responseProcessingTime = Duration.nanosSince(responseStart);
                    long bytesWritten = outputStream != null ? outputStream.getCount() : 0;
                    stats.record(request.getMethod(),
                            response.getStatusCode(),
                            bytesWritten,
                            response.getBytesRead(),
                            schedulingDelay,
                            requestProcessingTime,
                            responseProcessingTime);
                }
            }
            finally {
                Closeables.closeQuietly(outputStream);
                Response.dispose(response);
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
    }

    private static class ExceptionFromResponseHandler extends Exception
    {
        private ExceptionFromResponseHandler(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }
}

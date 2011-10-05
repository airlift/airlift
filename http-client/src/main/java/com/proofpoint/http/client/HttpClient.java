package com.proofpoint.http.client;

import com.google.common.base.Preconditions;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.AbstractCheckedFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class HttpClient
{
    private final ExecutorService executor;

    public HttpClient(ExecutorService executor)
    {
        this.executor = executor;
    }

    public <T, E extends Exception> CheckedFuture<T, E> execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        // todo replace with ListeningExecutorService in Guava r10
        ListenableFutureTask<T> listenableFutureTask = new ListenableFutureTask<T>(new HttpExecution<T>(request, responseHandler));
        executor.execute(listenableFutureTask);
        return new ResponseFuture<T, E>(request, responseHandler, listenableFutureTask);
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
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) request.getUri().toURL().openConnection(Proxy.NO_PROXY);
                urlConnection.setRequestMethod(request.getMethod());
                for (Entry<String, String> entry : request.getHeaders().entries()) {
                    urlConnection.addRequestProperty(entry.getKey(), entry.getValue());
                }

                if (request.getBodyGenerator() != null) {
                    urlConnection.setDoOutput(true);
                    urlConnection.setChunkedStreamingMode(4096);
                    outputStream = urlConnection.getOutputStream();
                    request.getBodyGenerator().write(outputStream);
                    outputStream.close();
                }

                // Get the response
                Response response = new Response(urlConnection);
                try {
                    return responseHandler.handle(request, response);
                }
                catch (Exception e) {
                    throw new ExceptionFromResponseHandler(e);
                }
            }
            finally {
                Closeables.closeQuietly(outputStream);
                Closeables.closeQuietly(inputStream);
            }

        }
    }

    private class ResponseFuture<T, E extends Exception> extends AbstractCheckedFuture<T, E>
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

package io.airlift.http.client;

import com.google.common.util.concurrent.CheckedFuture;

public interface AsyncHttpClient
        extends HttpClient
{
    <T, E extends Exception> AsyncHttpResponseFuture<T, E> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
            throws E;

    public interface AsyncHttpResponseFuture<T, E extends Exception>
            extends CheckedFuture<T, E>
    {
        String getState();
    }
}

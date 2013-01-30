package io.airlift.http.client;

import com.google.common.util.concurrent.CheckedFuture;

public interface AsyncHttpClient
        extends HttpClient
{
    <T, E extends Exception> CheckedFuture<T, E> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
            throws E;
}

package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;

@Beta
public interface AsyncHttpClient
        extends HttpClient
{
    <T, E extends Exception> AsyncHttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler);

    public interface AsyncHttpResponseFuture<T>
            extends ListenableFuture<T>
    {
        /**
         * State for diagnostics.  Do not rely on values from this method.
         */
        String getState();
    }
}

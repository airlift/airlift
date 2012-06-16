package io.airlift.http.client;

import com.google.common.annotations.Beta;

@Beta
public interface ResponseHandler<T, E extends Exception>
{
    E handleException(Request request, Exception exception);

    T handle(Request request, Response response)
            throws E;
}

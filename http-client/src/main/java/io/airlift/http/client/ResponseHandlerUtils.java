package io.airlift.http.client;

import com.google.common.base.Throwables;

import java.io.IOException;
import java.net.ConnectException;

public final class ResponseHandlerUtils
{
    private ResponseHandlerUtils()
    {
    }

    public static RuntimeException propagate(Request request, Throwable exception)
    {
        if (exception instanceof ConnectException) {
            throw new RuntimeIOException("Server refused connection: " + request.getUri().toASCIIString(), (ConnectException) exception);
        }
        if (exception instanceof IOException) {
            throw new RuntimeIOException((IOException) exception);
        }
        throw Throwables.propagate(exception);
    }
}

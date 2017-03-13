package io.airlift.http.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;

import static com.google.common.base.Throwables.throwIfUnchecked;

public final class ResponseHandlerUtils
{
    private ResponseHandlerUtils()
    {
    }

    public static RuntimeException propagate(Request request, Throwable exception)
    {
        if (exception instanceof ConnectException) {
            throw new UncheckedIOException("Server refused connection: " + request.getUri().toASCIIString(), (ConnectException) exception);
        }
        if (exception instanceof IOException) {
            throw new UncheckedIOException((IOException) exception);
        }
        throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }
}

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
        if (exception instanceof ConnectException connectException) {
            throw new UncheckedIOException("Server refused connection: " + urlFor(request), connectException);
        }
        if (exception instanceof IOException ioException) {
            throw new UncheckedIOException("Failed communicating with server: " + urlFor(request), ioException);
        }
        throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }

    public static byte[] readResponseBytes(Request request, Response response)
    {
        try {
            return response.getInputStream().readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed reading response from server: " + urlFor(request), e);
        }
    }

    private static String urlFor(Request request)
    {
        return request.getUri().toASCIIString();
    }
}

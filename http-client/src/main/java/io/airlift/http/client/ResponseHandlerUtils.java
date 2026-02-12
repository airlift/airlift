package io.airlift.http.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            throw new UncheckedIOException("Server refused connection: " + urlFor(request), (ConnectException) exception);
        }
        if (exception instanceof IOException) {
            throw new UncheckedIOException("Failed communicating with server: " + urlFor(request), (IOException) exception);
        }
        throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }

    public static byte[] getResponseBytes(Request request, Response response)
    {
        try {
            return switch (response.getContent()) {
                case Response.BytesContent(byte[] bytes) -> bytes;
                case Response.InputStreamContent(InputStream inputStream) -> inputStream.readAllBytes();
            };
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed reading response from server: " + urlFor(request), e);
        }
    }

    public static InputStream getResponseStream(Response response)
    {
        return switch (response.getContent()) {
            case Response.BytesContent(byte[] bytes) -> new ByteArrayInputStream(bytes);
            case Response.InputStreamContent(InputStream inputStream) -> inputStream;
        };
    }

    private static String urlFor(Request request)
    {
        return request.getUri().toASCIIString();
    }
}

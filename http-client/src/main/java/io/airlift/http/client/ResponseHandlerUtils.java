package io.airlift.http.client;

import com.google.common.net.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.RequestDiagnostics.sanitizeUri;
import static io.airlift.http.client.UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public final class ResponseHandlerUtils
{
    private ResponseHandlerUtils() {}

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

    public static UnexpectedResponseException captureUnexpectedResponse(String message, Request request, Response response)
    {
        return captureUnexpectedResponse(message, request, response, DEFAULT_MAX_RESPONSE_BODY_BYTES);
    }

    public static UnexpectedResponseException captureUnexpectedResponse(String message, Request request, Response response, int maxResponseBodyBytes)
    {
        requireNonNull(response, "response is null");
        checkArgument(maxResponseBodyBytes >= 0, "maxResponseBodyBytes is negative");
        checkArgument(maxResponseBodyBytes < Integer.MAX_VALUE, "maxResponseBodyBytes is too large");

        CapturedBody capturedBody = captureBody(response, maxResponseBodyBytes);
        return new UnexpectedResponseException(
                message,
                request,
                response.getStatusCode(),
                response.getHeaders(),
                capturedBody.bytes(),
                capturedBody.truncated(),
                capturedBody.failure());
    }

    public static boolean isJsonUtf8Content(Response response)
    {
        return response.getHeader(CONTENT_TYPE)
                .map(MediaType::parse)
                // Empty charset is considered UTF-8
                .map(type -> type.type().equals("application") && type.subtype().equals("json") && type.charset().toJavaUtil().map(UTF_8::equals).orElse(true))
                .orElse(false);
    }

    public static boolean isEventStreamContent(Response response)
    {
        try {
            return response.getHeader(CONTENT_TYPE)
                    .map(MediaType::parse)
                    .map(type -> type.type().equals("text") && type.subtype().equals("event-stream"))
                    .orElse(false);
        }
        catch (IllegalArgumentException e) {
            // a malformed content type is not an event stream
            return false;
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
        return sanitizeUri(request.getUri()).toASCIIString();
    }

    private static CapturedBody captureBody(Response response, int maxResponseBodyBytes)
    {
        try {
            return switch (response.getContent()) {
                case Response.BytesContent(byte[] bytes) -> new CapturedBody(
                        Arrays.copyOf(bytes, Math.min(bytes.length, maxResponseBodyBytes)),
                        bytes.length > maxResponseBodyBytes);
                case Response.InputStreamContent(InputStream inputStream) -> {
                    byte[] bytes = inputStream.readNBytes(maxResponseBodyBytes + 1);
                    yield new CapturedBody(
                            Arrays.copyOf(bytes, Math.min(bytes.length, maxResponseBodyBytes)),
                            bytes.length > maxResponseBodyBytes);
                }
            };
        }
        catch (IOException e) {
            return new CapturedBody(new byte[0], false, new UncheckedIOException("Failed reading response body", e));
        }
    }

    private record CapturedBody(byte[] bytes, boolean truncated, UncheckedIOException failure)
    {
        private CapturedBody(byte[] bytes, boolean truncated)
        {
            this(bytes, truncated, null);
        }
    }
}

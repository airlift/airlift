package io.airlift.http.client;

import com.google.common.net.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.util.Optional;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    public static boolean isJsonUtf8Content(Response response)
    {
        Optional<String> contentType = response.getHeader(CONTENT_TYPE);
        if (contentType.isEmpty()) {
            return false;
        }
        String value = contentType.get();
        // Fast path for the overwhelmingly common values, avoiding the relatively expensive (and
        // allocation-heavy) MediaType.parse() on every JSON response.
        if (isJsonUtf8ContentType(value)) {
            return true;
        }
        // Empty charset is considered UTF-8
        return Optional.of(value)
                .map(MediaType::parse)
                .map(type -> type.type().equals("application") && type.subtype().equals("json") && type.charset().toJavaUtil().map(UTF_8::equals).orElse(true))
                .orElse(false);
    }

    /**
     * Recognizes {@code application/json} optionally followed by a UTF-8 charset parameter,
     * case-insensitively and without allocating. Any other shape returns {@code false} so the caller
     * falls back to full {@link MediaType} parsing, preserving the existing semantics (including the
     * rejection of malformed values). Because it only returns {@code true} for values that
     * {@code MediaType.parse()} would also accept as application/json with a UTF-8 (or absent)
     * charset, it never changes the result.
     */
    private static boolean isJsonUtf8ContentType(String value)
    {
        if (!value.regionMatches(true, 0, "application/json", 0, 16)) {
            return false;
        }
        int length = value.length();
        int index = 16;
        if (index == length) {
            return true;
        }
        if (value.charAt(index) != ';') {
            return false;
        }
        index++;
        if (index < length && value.charAt(index) == ' ') {
            index++;
        }
        return value.regionMatches(true, index, "charset=utf-8", 0, 13) && (index + 13 == length);
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

package io.airlift.http.client;

import com.google.common.net.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;

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
        return response.getHeader(CONTENT_TYPE)
                .map(MediaType::parse)
                // Empty charset is considered UTF-8
                .map(type -> type.type().equals("application") && type.subtype().equals("json") && type.charset().toJavaUtil().map(UTF_8::equals).orElse(true))
                .orElse(false);
    }

    public static InputStream getResponseStream(Response response)
    {
        return switch (response.getContent()) {
            case Response.BytesContent(byte[] bytes) -> new ByteArrayInputStream(bytes);
            case Response.InputStreamContent(InputStream inputStream) -> inputStream;
        };
    }

    /**
     * Returns {@code uri} without user info, query, or fragment, for messages and logs that must not expose credentials.
     */
    public static URI sanitizeUri(URI uri)
    {
        if (uri == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder();
        if (uri.getScheme() != null) {
            sanitized.append(uri.getScheme()).append(':');
        }
        if (uri.getRawAuthority() != null) {
            String authority = uri.getRawAuthority();
            int userInfoEnd = authority.lastIndexOf('@');
            if (userInfoEnd >= 0) {
                authority = authority.substring(userInfoEnd + 1);
            }
            sanitized.append("//").append(authority);
        }
        if (uri.getRawPath() != null) {
            sanitized.append(uri.getRawPath());
        }
        return URI.create(sanitized.toString());
    }

    private static String urlFor(Request request)
    {
        return sanitizeUri(request.getUri()).toASCIIString();
    }
}

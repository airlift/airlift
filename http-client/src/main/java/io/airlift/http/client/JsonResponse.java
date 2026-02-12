package io.airlift.http.client;

import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;

import java.nio.charset.Charset;
import java.util.Collection;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public sealed interface JsonResponse<T>
{
    T jsonValue();

    Request request();

    record JsonValue<T>(Request request, int statusCode, Multimap<HeaderName, String> headers, T jsonValue, long bytesRead)
            implements JsonResponse<T>
    {
        public JsonValue
        {
            requireNonNull(request, "request is null");
            requireNonNull(headers, "request is null");
            requireNonNull(jsonValue, "jsonValue is null");
        }
    }

    record Exception<T>(Request request, int statusCode, Throwable throwable)
            implements JsonResponse<T>
    {
        public Exception
        {
            requireNonNull(request, "request is null");
            requireNonNull(throwable, "throwable is null");
        }

        @Override
        public T jsonValue()
        {
            throw new IllegalStateException("Response does not contain a JSON value", throwable);
        }
    }

    record NonJsonBytes<T>(Request request, int statusCode, Multimap<HeaderName, String> headers, byte[] responseBytes, Throwable throwable)
            implements JsonResponse<T>
    {
        public NonJsonBytes
        {
            requireNonNull(request, "request is null");
            requireNonNull(headers, "headers is null");
            requireNonNull(responseBytes, "responseBytes is null");
            requireNonNull(throwable, "throwable is null");
        }

        @Override
        public T jsonValue()
        {
            throw new IllegalStateException("Could not decode response to JSON", throwable);
        }

        public String stringValue()
        {
            return new String(responseBytes, charset());
        }

        public Charset charset()
        {
            String value = getHeader(CONTENT_TYPE);
            if (value != null) {
                try {
                    return MediaType.parse(value).charset().or(UTF_8);
                }
                catch (RuntimeException ignored) {
                }
            }
            return UTF_8;
        }

        public String getHeader(String name)
        {
            Collection<String> values = headers.get(HeaderName.of(name));
            return values.isEmpty() ? null : values.stream().findFirst().orElse(null);
        }
    }
}

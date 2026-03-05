package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public sealed interface JsonResponse<T>
{
    T jsonValue();

    Optional<Throwable> exception();

    Request request();

    Multimap<HeaderName, String> headers();

    default List<String> getHeader(String name)
    {
        return ImmutableList.copyOf(headers().get(HeaderName.of(name)));
    }

    int statusCode();

    record JsonValue<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, @Override T jsonValue, long bytesRead)
            implements JsonResponse<T>
    {
        public JsonValue
        {
            requireNonNull(request, "request is null");
            requireNonNull(headers, "request is null");
            requireNonNull(jsonValue, "jsonValue is null");
        }

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.empty();
        }
    }

    record Exception<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, Throwable throwable)
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

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.of(throwable);
        }
    }

    record NonJsonBytes<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, byte[] responseBytes, Throwable throwable)
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

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.of(throwable);
        }

        public String stringValue()
        {
            return new String(responseBytes, charset());
        }

        public Charset charset()
        {
            String value = getHeader(CONTENT_TYPE).stream()
                    .findFirst()
                    .orElse(null);
            if (value != null) {
                try {
                    return MediaType.parse(value).charset().or(UTF_8);
                }
                catch (RuntimeException ignored) {
                }
            }
            return UTF_8;
        }
    }
}

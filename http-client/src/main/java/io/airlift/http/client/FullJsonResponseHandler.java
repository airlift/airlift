/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.json.JsonCodec;
import jakarta.annotation.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.isJsonUtf8Content;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * FullJsonResponseHandler is a {@link ResponseHandler} that creates a JSON entity from the response bytes when Content-Type
 * is application/json. In contrast to {@link StreamingJsonResponseHandler} it keeps the whole response, and joins it into
 * a byte[] when the response bytes are requested. This allows for retrieval of the response bytes if decoding JSON fails,
 * but at the same time keeps the response in memory. If the response is rather large and debugabillity can be sacrified,
 * {@link StreamingJsonResponseHandler} should be used instead.
 */
public class FullJsonResponseHandler<T>
        implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    public static <T> FullJsonResponseHandler<T> createFullJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new FullJsonResponseHandler<>(jsonCodec);
    }

    private final JsonCodec<T> jsonCodec;

    private FullJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public JsonResponse<T> handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public JsonResponse<T> handle(Request request, Response response)
    {
        JsonResponseBody body = JsonResponseBody.of(request, response);
        if (!isJsonUtf8Content(response)) {
            return new JsonResponse<>(response.getStatusCode(), response.getHeaders(), body);
        }
        return new JsonResponse<>(response.getStatusCode(), response.getHeaders(), jsonCodec, body);
    }

    public static class JsonResponse<T>
    {
        private final int statusCode;
        private final ListMultimap<HeaderName, String> headers;
        private final boolean hasValue;
        private final boolean json;
        private final JsonResponseBody body;
        private final T value;
        private final IllegalArgumentException exception;

        public JsonResponse(int statusCode, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
        {
            this(statusCode, headers, JsonResponseBody.of(requireNonNull(responseBytes, "responseBytes is null")));
        }

        private JsonResponse(int statusCode, ListMultimap<HeaderName, String> headers, JsonResponseBody body)
        {
            this.statusCode = statusCode;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.json = false;
            this.body = requireNonNull(body, "body is null");
            this.value = null;
            this.exception = null;
        }

        public JsonResponse(int statusCode, ListMultimap<HeaderName, String> headers, JsonCodec<T> jsonCodec, byte[] jsonBytes)
        {
            this(statusCode, headers, jsonCodec, JsonResponseBody.of(requireNonNull(jsonBytes, "jsonBytes is null")));
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        private JsonResponse(int statusCode, ListMultimap<HeaderName, String> headers, JsonCodec<T> jsonCodec, JsonResponseBody body)
        {
            this.statusCode = statusCode;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.json = true;
            this.body = requireNonNull(body, "body is null");

            T value = null;
            IllegalArgumentException exception = null;
            try {
                value = body.parseJson(jsonCodec);
            }
            catch (IllegalArgumentException e) {
                exception = new IllegalArgumentException("Unable to create %s from JSON response:\n[%s]".formatted(jsonCodec.getType(), getJson()), e);
            }
            this.hasValue = (exception == null);
            this.value = value;
            this.exception = exception;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        /**
         * @deprecated Use {@link #getHeader(HeaderName)} instead
         */
        @Nullable
        @Deprecated
        public String getHeader(String name)
        {
            return getHeader(HeaderName.of(name)).orElse(null);
        }

        public Optional<String> getHeader(HeaderName name)
        {
            List<String> values = getHeaders().get(name);
            return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.getFirst());
        }

        public List<String> getHeaders(HeaderName name)
        {
            return headers.get(name);
        }

        /**
         *  @deprecated Use {@link #getHeaders(HeaderName)} instead
         */
        @Deprecated
        public List<String> getHeaders(String name)
        {
            return getHeaders(HeaderName.of(name));
        }

        public ListMultimap<HeaderName, String> getHeaders()
        {
            return headers;
        }

        public boolean hasValue()
        {
            return hasValue;
        }

        public T getValue()
        {
            if (!hasValue) {
                throw new IllegalStateException("Response does not contain a JSON value", exception);
            }
            return value;
        }

        public int getResponseSize()
        {
            return body.length();
        }

        public byte[] getResponseBytes()
        {
            return body.bytes().clone();
        }

        public String getResponseBody()
        {
            return new String(body.bytes(), getCharset());
        }

        public byte[] getJsonBytes()
        {
            if (!json) {
                return null;
            }
            return body.bytes().clone();
        }

        public String getJson()
        {
            if (!json) {
                return null;
            }
            return new String(body.bytes(), UTF_8);
        }

        public IllegalArgumentException getException()
        {
            return exception;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("statusCode", statusCode)
                    .add("headers", headers)
                    .add("hasValue", hasValue)
                    .add("value", value)
                    .toString();
        }

        private Charset getCharset()
        {
            try {
                return getHeader(CONTENT_TYPE)
                        .map(MediaType::parse)
                        .map(MediaType::charset)
                        .flatMap(optional -> optional.toJavaUtil())
                        .orElse(UTF_8);
            }
            catch (RuntimeException e) {
                return UTF_8;
            }
        }
    }
}

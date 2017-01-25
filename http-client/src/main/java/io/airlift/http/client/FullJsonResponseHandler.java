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
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.json.JsonCodec;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class FullJsonResponseHandler<T>
        implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

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
        byte[] bytes = readResponseBytes(response);
        String contentType = response.getHeader(CONTENT_TYPE);
        if ((contentType == null) || !MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
            return new JsonResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), bytes);
        }
        return new JsonResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), jsonCodec, bytes);
    }

    private static byte[] readResponseBytes(Response response)
    {
        try {
            return ByteStreams.toByteArray(response.getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server", e);
        }
    }

    public static class JsonResponse<T>
    {
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<HeaderName, String> headers;
        private final boolean hasValue;
        private final byte[] jsonBytes;
        private final byte[] responseBytes;
        private final T value;
        private final IllegalArgumentException exception;

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.jsonBytes = null;
            this.responseBytes = requireNonNull(responseBytes, "responseBytes is null");
            this.value = null;
            this.exception = null;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        public JsonResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, JsonCodec<T> jsonCodec, byte[] jsonBytes)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.jsonBytes = requireNonNull(jsonBytes, "jsonBytes is null");
            this.responseBytes = requireNonNull(jsonBytes, "responseBytes is null");

            T value = null;
            IllegalArgumentException exception = null;
            try {
                value = jsonCodec.fromJson(jsonBytes);
            }
            catch (IllegalArgumentException e) {
                exception = new IllegalArgumentException(format("Unable to create %s from JSON response:\n[%s]", jsonCodec.getType(), getJson()), e);
            }
            this.hasValue = (exception == null);
            this.value = value;
            this.exception = exception;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public String getStatusMessage()
        {
            return statusMessage;
        }

        @Nullable
        public String getHeader(String name)
        {
            List<String> values = getHeaders().get(HeaderName.of(name));
            return values.isEmpty() ? null : values.get(0);
        }

        public List<String> getHeaders(String name)
        {
            return headers.get(HeaderName.of(name));
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
            return responseBytes.length;
        }

        public byte[] getResponseBytes()
        {
            return responseBytes.clone();
        }

        public String getResponseBody()
        {
            return new String(responseBytes, getCharset());
        }

        public byte[] getJsonBytes()
        {
            return (jsonBytes == null) ? null : jsonBytes.clone();
        }

        public String getJson()
        {
            return (jsonBytes == null) ? null : new String(jsonBytes, UTF_8);
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
                    .add("statusMessage", statusMessage)
                    .add("headers", headers)
                    .add("hasValue", hasValue)
                    .add("value", value)
                    .toString();
        }

        private Charset getCharset()
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
    }
}

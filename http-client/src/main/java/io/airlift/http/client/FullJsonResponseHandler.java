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

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.json.JsonCodec;

import javax.annotation.concurrent.NotThreadSafe;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.util.List;

public class FullJsonResponseHandler<T> implements ResponseHandler<JsonResponse<T>, RuntimeException>
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
    public RuntimeException handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            return new RuntimeException("Server refused connection: " + request.getUri().toASCIIString());
        }
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        }
        return new RuntimeException(exception);
    }

    @Override
    public JsonResponse<T> handle(Request request, Response response)
    {
        String contentType = response.getHeader("Content-Type");
        if ((contentType == null) || !MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
            return new JsonResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders());
        }
        try {
            String json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
            return new JsonResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), jsonCodec, json);
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading JSON response from server", e);
        }
    }

    @NotThreadSafe
    public static class JsonResponse<T>
    {
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<String, String> headers;
        private final boolean hasValue;
        private final String json;
        private final T value;
        private final IllegalArgumentException exception;

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.json = null;
            this.value = null;
            this.exception = null;
        }

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers, JsonCodec<T> jsonCodec, String json)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.json = json;

            T value = null;
            IllegalArgumentException exception = null;
            try {
                value = jsonCodec.fromJson(json);
            }
            catch (IllegalArgumentException e) {
                exception = e;
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

        public String getHeader(String name)
        {
            List<String> values = getHeaders().get(name);
            if (values.isEmpty()) {
                return null;
            }
            return values.get(0);
        }

        public ListMultimap<String, String> getHeaders()
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

        public String getJson()
        {
            return json;
        }

        public IllegalArgumentException getException()
        {
            return exception;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("statusCode", statusCode)
                    .add("statusMessage", statusMessage)
                    .add("headers", headers)
                    .add("hasValue", hasValue)
                    .add("value", value)
                    .toString();
        }
    }
}

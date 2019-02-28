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

import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.json.Codec;
import io.airlift.json.JsonCodec;

import java.nio.charset.Charset;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class FullJsonResponseHandler<T>
        extends ResponseHandler<JsonResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    public static <T> FullJsonResponseHandler<T> createFullJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new FullJsonResponseHandler<>(jsonCodec);
    }

    private FullJsonResponseHandler(Codec<T> jsonCodec)
    {
        super(jsonCodec);
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
            return new JsonResponse<T>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), bytes);
        }
        return new JsonResponse(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), codec.get(), bytes);
    }

    public static class JsonResponse<T>
            extends ValueResponse<T>
    {
        private final byte[] jsonBytes;

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
        {
            super(statusCode, statusMessage, headers, responseBytes);
            this.jsonBytes = null;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        public JsonResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers, Codec<T> jsonCodec, byte[] jsonBytes)
        {
            super(statusCode, statusMessage, headers, jsonCodec, jsonBytes);
            this.jsonBytes = requireNonNull(jsonBytes, "jsonBytes is null");
        }

        @Override
        protected String toResponseString(byte[] responseBytes)
        {
            return new String(responseBytes, UTF_8);
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

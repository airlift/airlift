/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import com.proofpoint.http.client.FullSmileResponseHandler.SmileResponse;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.ObjectMapperProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.proofpoint.http.client.ResponseHandlerUtils.propagate;

public class FullSmileResponseHandler<T>
        implements ResponseHandler<SmileResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_SMILE = MediaType.create("application", "x-jackson-smile");

    public static <T> FullSmileResponseHandler<T> createFullSmileResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new FullSmileResponseHandler<>(jsonCodec);
    }

    private final JsonCodec<T> jsonCodec;

    private FullSmileResponseHandler(JsonCodec<T> jsonCodec)
    {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public SmileResponse<T> handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public SmileResponse<T> handle(Request request, Response response)
    {
        String contentType = response.getHeader(CONTENT_TYPE);
        if ((contentType == null) || !MediaType.parse(contentType).is(MEDIA_TYPE_SMILE)) {
            return new SmileResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders());
        }
        try {
            return new SmileResponse<>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), jsonCodec, response.getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading SMILE response from server", e);
        }
    }

    public static class SmileResponse<T>
    {
        private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(new Supplier<ObjectMapper>()
        {
            @Override
            public ObjectMapper get()
            {
                return new ObjectMapperProvider().get();
            }
        });

        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<String, String> headers;
        private final boolean hasValue;
        private final T value;
        private final IllegalArgumentException exception;

        public SmileResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.value = null;
            this.exception = null;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        public SmileResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers, JsonCodec<T> jsonCodec, InputStream inputStream)
                throws IOException
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            T value = null;
            IllegalArgumentException exception = null;
            try {
                JsonParser jsonParser = new SmileFactory().createParser(inputStream);
                ObjectMapper objectMapper = OBJECT_MAPPER_SUPPLIER.get();

                // Important: we are NOT to close the underlying stream after
                // mapping, so we need to instruct parser:
                jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

                value = objectMapper.readValue(jsonParser, objectMapper.getTypeFactory().constructType(jsonCodec.getType()));
            }
            catch (InvalidFormatException e) {
                exception = new IllegalArgumentException("Unable to create " + jsonCodec.getType() + " from SMILE response", e);
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
                throw new IllegalStateException("Response does not contain a SMILE value", exception);
            }
            return value;
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
    }
}

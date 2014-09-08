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
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.ObjectMapperProvider;

import java.io.IOException;
import java.util.Set;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.proofpoint.http.client.ResponseHandlerUtils.propagate;

public class SmileResponseHandler<T> implements ResponseHandler<T, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_SMILE = MediaType.create("application", "x-jackson-smile");
    private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(new Supplier<ObjectMapper>()
    {
        @Override
        public ObjectMapper get()
        {
            return new ObjectMapperProvider().get();
        }
    });

    public static <T> SmileResponseHandler<T> createSmileResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new SmileResponseHandler<>(jsonCodec);
    }

    public static <T> SmileResponseHandler<T> createSmileResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new SmileResponseHandler<>(jsonCodec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private final JsonCodec<T> jsonCodec;
    private final Set<Integer> successfulResponseCodes;

    private SmileResponseHandler(JsonCodec<T> jsonCodec)
    {
        this(jsonCodec, 200, 201, 202, 203, 204, 205, 206);
    }

    private SmileResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.jsonCodec = jsonCodec;
        this.successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    @Override
    public T handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (!successfulResponseCodes.contains(response.getStatusCode())) {
            throw new UnexpectedResponseException(
                    String.format("Expected response code to be %s, but was %d: %s", successfulResponseCodes, response.getStatusCode(), response.getStatusMessage()),
                    request,
                    response);
        }
        String contentType = response.getHeader(CONTENT_TYPE);
        if (contentType == null) {
            throw new UnexpectedResponseException("Content-Type is not set for response", request, response);
        }
        if (!MediaType.parse(contentType).is(MEDIA_TYPE_SMILE)) {
            throw new UnexpectedResponseException("Expected application/x-jackson-smile response from server but got " + contentType, request, response);
        }
        try {
            JsonParser jsonParser = new SmileFactory().createParser(response.getInputStream());
            ObjectMapper objectMapper = OBJECT_MAPPER_SUPPLIER.get();

            // Important: we are NOT to close the underlying stream after
            // mapping, so we need to instruct parser:
            jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

            return objectMapper.readValue(jsonParser, objectMapper.getTypeFactory().constructType(jsonCodec.getType()));
        }
        catch (InvalidFormatException e) {
            throw new IllegalArgumentException("Unable to create " + jsonCodec.getType() + " from SMILE response", e);
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading SMILE response from server", e);
        }
    }
}

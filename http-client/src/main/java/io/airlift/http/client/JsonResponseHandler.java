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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import io.airlift.json.JsonCodec;

import java.io.IOException;
import java.util.Set;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonResponseHandler<T> implements ResponseHandler<T, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    public static <T> JsonResponseHandler<T> createJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new JsonResponseHandler<>(jsonCodec);
    }

    public static <T> JsonResponseHandler<T> createJsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonResponseHandler<>(jsonCodec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private final JsonCodec<T> jsonCodec;
    private final Set<Integer> successfulResponseCodes;

    private JsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this(jsonCodec, 200, 201, 202, 203, 204, 205, 206);
    }

    private JsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
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
        if (!MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
            throw new UnexpectedResponseException("Expected application/json response from server but got " + contentType, request, response);
        }
        byte[] bytes;
        try {
            bytes = ByteStreams.toByteArray(response.getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server");
        }
        try {
            return jsonCodec.fromJson(bytes);
        }
        catch (IllegalArgumentException e) {
            String json = new String(bytes, UTF_8);
            throw new IllegalArgumentException(String.format("Unable to create %s from JSON response:\n[%s]", jsonCodec.getType(), json), e);
        }
    }
}

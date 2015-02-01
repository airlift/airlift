/*
 * Copyright 2013 Proofpoint, Inc.
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

import java.util.Set;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;

public class DefaultingJsonResponseHandler<T>
        implements ResponseHandler<T, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    public static <T> DefaultingJsonResponseHandler<T> createDefaultingJsonResponseHandler(JsonCodec<T> jsonCodec, T defaultValue)
    {
        return new DefaultingJsonResponseHandler<>(jsonCodec, defaultValue);
    }

    public static <T> DefaultingJsonResponseHandler<T> createDefaultingJsonResponseHandler(JsonCodec<T> jsonCodec, T defaultValue, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new DefaultingJsonResponseHandler<>(jsonCodec, defaultValue, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private final JsonCodec<T> jsonCodec;
    private final T defaultValue;
    private final Set<Integer> successfulResponseCodes;

    private DefaultingJsonResponseHandler(JsonCodec<T> jsonCodec, T defaultValue)
    {
        this(jsonCodec, defaultValue, 200, 201, 202, 203, 204, 205, 206);
    }

    private DefaultingJsonResponseHandler(JsonCodec<T> jsonCodec, T defaultValue, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.jsonCodec = jsonCodec;
        this.defaultValue = defaultValue;
        this.successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    @Override
    public T handleException(Request request, Exception exception)
    {
        return defaultValue;
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (!successfulResponseCodes.contains(response.getStatusCode())) {
            return defaultValue;
        }
        String contentType = response.getHeader(CONTENT_TYPE);
        if (!MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
            return defaultValue;
        }
        try {
            return jsonCodec.fromJson(ByteStreams.toByteArray(response.getInputStream()));
        }
        catch (Exception e) {
            return defaultValue;
        }
    }
}

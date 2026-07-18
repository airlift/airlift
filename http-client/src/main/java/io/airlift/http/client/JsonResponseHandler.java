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
import com.google.common.primitives.Ints;
import io.airlift.json.JsonCodec;

import java.util.Set;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.captureUnexpectedResponse;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.isJsonUtf8Content;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonResponseHandler<T>
        implements ResponseHandler<T, RuntimeException>
{
    public static <T> JsonResponseHandler<T> createJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new JsonResponseHandler<>(jsonCodec);
    }

    public static <T> JsonResponseHandler<T> createJsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonResponseHandler<>(jsonCodec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    public static <T> JsonResponseHandler<T> createSuccessfulJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new JsonResponseHandler<>(jsonCodec, true, true, Set.of());
    }

    public static <T> JsonResponseHandler<T> createSafeJsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonResponseHandler<>(jsonCodec, false, true, ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build());
    }

    private final JsonCodec<T> jsonCodec;
    private final Set<Integer> successfulResponseCodes;
    private final boolean acceptAnySuccessfulResponse;
    private final boolean safeDiagnostics;

    private JsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this(jsonCodec, 200, 201, 202, 203, 204, 205, 206);
    }

    private JsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.jsonCodec = jsonCodec;
        this.successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
        acceptAnySuccessfulResponse = false;
        safeDiagnostics = false;
    }

    private JsonResponseHandler(JsonCodec<T> jsonCodec, boolean acceptAnySuccessfulResponse, boolean safeDiagnostics, Set<Integer> successfulResponseCodes)
    {
        this.jsonCodec = jsonCodec;
        this.successfulResponseCodes = Set.copyOf(successfulResponseCodes);
        this.acceptAnySuccessfulResponse = acceptAnySuccessfulResponse;
        this.safeDiagnostics = safeDiagnostics;
    }

    @Override
    public T handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (!(acceptAnySuccessfulResponse && isSuccessful(response.getStatusCode())) && !successfulResponseCodes.contains(response.getStatusCode())) {
            if (safeDiagnostics) {
                throw captureUnexpectedResponse(
                        "Expected %s response code, but was %d".formatted(
                                acceptAnySuccessfulResponse ? "a successful" : successfulResponseCodes,
                                response.getStatusCode()),
                        request,
                        response);
            }
            throw new UnexpectedResponseException(
                    "Expected response code to be %s, but was %d".formatted(successfulResponseCodes, response.getStatusCode()),
                    request,
                    response);
        }

        if (!isExpectedJsonContent(response)) {
            if (safeDiagnostics) {
                throw captureUnexpectedResponse(
                        "Expected %s response from server".formatted(JSON_UTF_8),
                        request,
                        response);
            }
            throw new UnexpectedResponseException("Expected %s response from server but got %s".formatted(JSON_UTF_8, response.getHeader(CONTENT_TYPE).orElse(null)), request, response);
        }

        // TODO avoid buffering whole response before invoking the JSON codec.
        // When response is an InputStream this requires additional data copy and increases peak memory usage.
        // The data buffering is used only for error reporting, so perhaps it can be applied only on a retry.
        byte[] bytes = getResponseBytes(request, response);

        try {
            return jsonCodec.fromJson(bytes);
        }
        catch (IllegalArgumentException e) {
            if (safeDiagnostics) {
                throw new IllegalArgumentException("Unable to create %s from JSON response".formatted(jsonCodec.getType()));
            }
            String json = new String(bytes, UTF_8);
            throw new IllegalArgumentException("Unable to create %s from JSON response: <%s>".formatted(jsonCodec.getType(), json), e);
        }
    }

    private static boolean isSuccessful(int statusCode)
    {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isExpectedJsonContent(Response response)
    {
        try {
            return isJsonUtf8Content(response);
        }
        catch (IllegalArgumentException e) {
            if (!safeDiagnostics) {
                throw e;
            }
            return false;
        }
    }
}

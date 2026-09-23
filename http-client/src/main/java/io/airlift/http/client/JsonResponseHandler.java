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

import io.airlift.json.JsonCodec;

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
        return new JsonResponseHandler<>(jsonCodec, SuccessfulResponseCodes.of(firstSuccessfulResponseCode, otherSuccessfulResponseCodes), false);
    }

    public static <T> JsonResponseHandler<T> createSuccessfulJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new JsonResponseHandler<>(jsonCodec, SuccessfulResponseCodes.anySuccessful(), true);
    }

    public static <T> JsonResponseHandler<T> createSafeJsonResponseHandler(JsonCodec<T> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonResponseHandler<>(jsonCodec, SuccessfulResponseCodes.of(firstSuccessfulResponseCode, otherSuccessfulResponseCodes), true);
    }

    private final JsonCodec<T> jsonCodec;
    private final SuccessfulResponseCodes successfulResponseCodes;
    private final boolean safeDiagnostics;

    private JsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this(jsonCodec, SuccessfulResponseCodes.of(200, 201, 202, 203, 204, 205, 206), false);
    }

    private JsonResponseHandler(JsonCodec<T> jsonCodec, SuccessfulResponseCodes successfulResponseCodes, boolean safeDiagnostics)
    {
        this.jsonCodec = jsonCodec;
        this.successfulResponseCodes = successfulResponseCodes;
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
        if (!successfulResponseCodes.contains(response.getStatusCode())) {
            String message = "Expected response code to be %s, but was %d".formatted(successfulResponseCodes, response.getStatusCode());
            throw safeDiagnostics ? captureUnexpectedResponse(message, request, response) : new UnexpectedResponseException(message, request, response);
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

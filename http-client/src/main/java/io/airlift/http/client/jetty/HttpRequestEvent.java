/*
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
package io.airlift.http.client.jetty;

import com.google.common.annotations.VisibleForTesting;
import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import io.airlift.http.client.jetty.HttpClientLogger.ResponseInfo;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpFields;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static io.airlift.http.client.TraceTokenRequestFilter.TRACETOKEN_HEADER;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

class HttpRequestEvent
{
    static final int NO_RESPONSE = -1;

    private final Instant timeStamp;
    private final String protocolVersion;
    private final String method;
    private final String requestUri;
    private final String traceToken;

    // Response-related fields can have the value of NO_RESPONSE for the requests
    // that didn't get any response (e.g., due to timeouts or other failures).
    private final long responseSize;
    private final int responseCode;
    private final long timeToLastByte;
    private final Optional<String> failureReason;

    private HttpRequestEvent(
            Instant timeStamp,
            String protocolVersion,
            String method,
            String requestUri,
            @Nullable String traceToken,
            long responseSize,
            int responseCode,
            long timeToLastByte,
            Optional<String> failureReason)
    {
        this.timeStamp = requireNonNull(timeStamp, "timeStamp is null");
        this.protocolVersion = requireNonNull(protocolVersion, "protocolVersion is null");
        this.method = requireNonNull(method, "method is null");
        this.requestUri = requireNonNull(requestUri, "requestUri is null");
        this.traceToken = traceToken;
        this.responseSize = responseSize;
        this.responseCode = responseCode;
        this.timeToLastByte = timeToLastByte;
        this.failureReason = requireNonNull(failureReason, "failureReason is null");
    }

    public Instant getTimeStamp()
    {
        return timeStamp;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public String getMethod()
    {
        return method;
    }

    public String getRequestUri()
    {
        return requestUri;
    }

    public String getTraceToken()
    {
        return traceToken;
    }

    public long getResponseSize()
    {
        return responseSize;
    }

    public String getResponseCode()
    {
        return failureReason.orElseGet(() -> Integer.toString(responseCode));
    }

    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }

    static HttpRequestEvent createHttpRequestEvent(RequestInfo requestInfo, ResponseInfo responseInfo)
    {
        requireNonNull(requestInfo, "requestInfo is null");
        requireNonNull(responseInfo, "responseInfo is null");
        Request request = requestInfo.getRequest();
        Optional<Response> response = responseInfo.getResponse();

        String requestUri = null;
        if (request.getURI() != null) {
            requestUri = request.getURI().toString();
        }

        String method = request.getMethod();
        if (method != null) {
            method = method.toUpperCase(Locale.US);
        }

        long responseSize = NO_RESPONSE;
        int responseCode = NO_RESPONSE;
        if (response.isPresent()) {
            responseSize = responseInfo.getResponseSize();
            responseCode = response.get().getStatus();
        }

        long timeToLastByte = max(responseInfo.getResponseTimestampMillis() - requestInfo.getRequestTimestampMillis(), 0L);

        return new HttpRequestEvent(
                Instant.ofEpochMilli(requestInfo.getRequestTimestampMillis()),
                request.getVersion().toString(),
                method,
                requestUri,
                getHeader(request, TRACETOKEN_HEADER),
                responseSize,
                responseCode,
                timeToLastByte,
                getFailureReason(responseInfo));
    }

    @VisibleForTesting
    static Optional<String> getFailureReason(ResponseInfo responseInfo)
    {
        Optional<Throwable> failure = responseInfo.getFailureCause();

        if (!failure.isPresent()) {
            return Optional.empty();
        }

        String className = failure.get().getClass().getSimpleName().toUpperCase(Locale.US);

        if (className.endsWith("EXCEPTION")) {
            return Optional.of(className.substring(0, className.lastIndexOf("EXCEPTION")));
        }

        return Optional.of(className);
    }

    @Nullable
    private static String getHeader(Request request, String header)
    {
        requireNonNull(header, "header is null");
        HttpFields headers = request.getHeaders();
        if (headers != null) {
            return headers.get(header);
        }
        return null;
    }
}

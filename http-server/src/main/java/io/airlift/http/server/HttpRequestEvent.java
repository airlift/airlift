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
package io.airlift.http.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.airlift.event.client.EventField;
import io.airlift.event.client.EventType;
import io.airlift.http.server.jetty.RequestTiming;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.security.Principal;
import java.time.Instant;
import java.util.Enumeration;

import static io.airlift.event.client.EventField.EventFieldMapping.TIMESTAMP;
import static io.airlift.http.server.DelimitedRequestLogHandler.REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE;
import static io.airlift.http.server.TraceTokenFilter.TRACETOKEN_HEADER;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@EventType("HttpRequest")
public record HttpRequestEvent(
        @EventField(fieldMapping = TIMESTAMP) Instant timeStamp,
        @EventField String traceToken,
        @EventField String clientAddress,
        @EventField String protocol,
        @EventField String method,
        @EventField String requestUri,
        @EventField String user,
        @EventField String agent,
        @EventField String referrer,
        @EventField long requestSize,
        @EventField String requestContentType,
        @EventField long responseSize,
        @EventField int responseCode,
        @EventField String responseContentType,
        @EventField long timeToDispatch,
        @EventField Long timeToFirstByte,
        @EventField long timeToLastByte,
        @EventField long beginToDispatchMillis,
        @EventField long beginToEndMillis,
        @EventField long firstToLastContentTimeInMillis,
        @EventField DoubleSummaryStats responseContentInterarrivalStats,
        @EventField String protocolVersion)
{
    public static HttpRequestEvent createHttpRequestEvent(Request request, Response response, TraceTokenManager traceTokenManager, RequestTiming timing)
    {
        String user = null;
        Request.AuthenticationState authenticationState = Request.getAuthenticationState(request);
        if (authenticationState != null) {
            Principal principal = authenticationState.getUserPrincipal();
            if (principal != null) {
                user = principal.getName();
            }
        }

        // This is required, because async responses are processed in a different thread.
        String token = request.getHeaders().get(TRACETOKEN_HEADER);
        if (token == null && traceTokenManager != null) {
            token = traceTokenManager.getCurrentRequestToken();
        }

        long timeToDispatch = NANOSECONDS.toMillis((long) requireNonNullElse(request.getAttribute(REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE), 0L));
        Long timeToFirstByte = null;
        Object firstByteTime = request.getAttribute(TimingFilter.FIRST_BYTE_TIME);
        if (firstByteTime instanceof Long time) {
            timeToFirstByte = max(time - Request.getTimeStamp(request), 0);
        }

        long timeToLastByte = max(timing.currentTimeInMillis() - Request.getTimeStamp(request), 0);

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (Request.getRemoteAddr(request) != null) {
            builder.add(Request.getRemoteAddr(request));
        }
        for (Enumeration<String> e = request.getHeaders().getValues("X-FORWARDED-FOR"); e != null && e.hasMoreElements(); ) {
            String forwardedFor = e.nextElement();
            builder.addAll(Splitter.on(',').trimResults().omitEmptyStrings().split(forwardedFor));
        }
        String clientAddress = null;
        ImmutableList<String> clientAddresses = builder.build();
        for (String address : Lists.reverse(clientAddresses)) {
            try {
                if (!Inet4Networks.isPrivateNetworkAddress(address)) {
                    clientAddress = address;
                    break;
                }
            }
            catch (IllegalArgumentException ignored) {
            }
        }
        if (clientAddress == null) {
            clientAddress = Request.getRemoteAddr(request);
        }

        String requestUri = null;
        if (request.getHttpURI() != null) {
            requestUri = request.getHttpURI().getPath();
            String parameters = request.getHttpURI().getQuery();
            if (parameters != null) {
                requestUri += "?" + parameters;
            }
        }

        String method = request.getMethod();
        if (method != null) {
            method = method.toUpperCase();
        }

        String protocol = request.getHeaders().get("X-FORWARDED-PROTO");
        if (protocol == null) {
            protocol = request.getHttpURI().getScheme();
        }
        if (protocol != null) {
            protocol = protocol.toLowerCase();
        }

        return new HttpRequestEvent(
                Instant.ofEpochMilli(Request.getTimeStamp(request)),
                token,
                clientAddress,
                protocol,
                method,
                requestUri,
                user,
                request.getHeaders().get("User-Agent"),
                request.getHeaders().get("Referer"),
                Request.getContentBytesRead(request),
                request.getHeaders().get("Content-Type"),
                Response.getContentBytesWritten(response),
                response.getStatus(),
                response.getHeaders().get("Content-Type"),
                timeToDispatch,
                timeToFirstByte,
                timeToLastByte,
                timing.beginToHandleMillis(),
                timing.beginToEndMillis(),
                timing.firstToLastContentTimeInMillis(),
                timing.responseContentInterarrivalStats(),
                request.getConnectionMetaData().getHttpVersion().asString());
    }
}

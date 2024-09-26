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
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.security.Principal;
import java.time.Instant;
import java.util.Enumeration;

import static io.airlift.event.client.EventField.EventFieldMapping.TIMESTAMP;

@EventType("HttpRequest")
public record HttpRequestEvent(
        @EventField(fieldMapping = TIMESTAMP) Instant timeStamp,
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
        @EventField long timeToHandle,
        @EventField long timeToFirstByte,
        @EventField long timeToLastByte,
        @EventField long timeToCompletion,
        @EventField long timeFromFirstToLastContent,
        @EventField DoubleSummaryStats responseContentInterarrivalStats,
        @EventField String protocolVersion)
{
    public static HttpRequestEvent createHttpRequestEvent(Request request, Response response, RequestTiming timing)
    {
        String user = null;
        Request.AuthenticationState authenticationState = Request.getAuthenticationState(request);
        if (authenticationState != null) {
            Principal principal = authenticationState.getUserPrincipal();
            if (principal != null) {
                user = principal.getName();
            }
        }

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
                timing.requestStarted(),
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
                timing.timeToDispatch().toMillis(),
                timing.timeToHandling().toMillis(),
                timing.timeToFirstByte().toMillis(),
                timing.timeToLastByte().toMillis(),
                timing.timeToCompletion().toMillis(),
                timing.timeToLastByte().toMillis() - timing.timeToFirstByte().toMillis(),
                timing.responseContentInterarrivalStats(),
                request.getConnectionMetaData().getHttpVersion().asString());
    }
}

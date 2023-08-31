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

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.event.client.EventField.EventFieldMapping.TIMESTAMP;
import static io.airlift.http.server.DelimitedRequestLogHandler.REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE;
import static io.airlift.http.server.TraceTokenFilter.TRACETOKEN_HEADER;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@EventType("HttpRequest")
public class HttpRequestEvent
{
    public static HttpRequestEvent createHttpRequestEvent(
            Request request,
            Response response,
            TraceTokenManager traceTokenManager,
            long currentTimeInMillis,
            RequestTiming timing,
            DoubleSummaryStats responseContentInterarrivalStats)
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

        long timeToDispatch = NANOSECONDS.toMillis((long) firstNonNull(request.getAttribute(REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE), 0L));
        Long timeToFirstByte = null;
        Object firstByteTime = request.getAttribute(TimingFilter.FIRST_BYTE_TIME);
        if (firstByteTime instanceof Long) {
            Long time = (Long) firstByteTime;
            timeToFirstByte = max(time - Request.getTimeStamp(request), 0);
        }

        long timeToLastByte = max(currentTimeInMillis - Request.getTimeStamp(request), 0);

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
                responseContentInterarrivalStats,
                request.getConnectionMetaData().getHttpVersion().asString());
    }

    private final Instant timeStamp;
    private final String traceToken;
    private final String clientAddress;
    private final String protocol;
    private final String method;
    private final String requestUri;
    private final String user;
    private final String agent;
    private final String referrer;
    private final long requestSize;
    private final String requestContentType;
    private final long responseSize;
    private final int responseCode;
    private final String responseContentType;
    private final long timeToDispatch;
    private final Long timeToFirstByte;
    private final long timeToLastByte;
    private final long beginToDispatchMillis;
    private final long beginToEndMillis;
    private final long firstToLastContentTimeInMillis;
    private final DoubleSummaryStats responseContentInterarrivalStats;
    private final String protocolVersion;

    public HttpRequestEvent(
            Instant timeStamp,
            String traceToken,
            String clientAddress,
            String protocol,
            String method,
            String requestUri,
            String user,
            String agent,
            String referrer,
            long requestSize,
            String requestContentType,
            long responseSize,
            int responseCode,
            String responseContentType,
            long timeToDispatch,
            Long timeToFirstByte,
            long timeToLastByte,
            long beginToDispatchMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            DoubleSummaryStats responseContentInterarrivalStats,
            String protocolVersion)
    {
        this.timeStamp = timeStamp;
        this.traceToken = traceToken;
        this.clientAddress = clientAddress;
        this.protocol = protocol;
        this.method = method;
        this.requestUri = requestUri;
        this.user = user;
        this.agent = agent;
        this.referrer = referrer;
        this.requestSize = requestSize;
        this.requestContentType = requestContentType;
        this.responseSize = responseSize;
        this.responseCode = responseCode;
        this.responseContentType = responseContentType;
        this.timeToDispatch = timeToDispatch;
        this.timeToFirstByte = timeToFirstByte;
        this.timeToLastByte = timeToLastByte;
        this.beginToDispatchMillis = beginToDispatchMillis;
        this.beginToEndMillis = beginToEndMillis;
        this.firstToLastContentTimeInMillis = firstToLastContentTimeInMillis;
        this.responseContentInterarrivalStats = responseContentInterarrivalStats;
        this.protocolVersion = protocolVersion;
    }

    @EventField(fieldMapping = TIMESTAMP)
    public Instant getTimeStamp()
    {
        return timeStamp;
    }

    @EventField
    public String getTraceToken()
    {
        return traceToken;
    }

    @EventField
    public String getClientAddress()
    {
        return clientAddress;
    }

    @EventField
    public String getProtocol()
    {
        return protocol;
    }

    @EventField
    public String getMethod()
    {
        return method;
    }

    @EventField
    public String getRequestUri()
    {
        return requestUri;
    }

    @EventField
    public String getUser()
    {
        return user;
    }

    @EventField
    public String getAgent()
    {
        return agent;
    }

    @EventField
    public String getReferrer()
    {
        return referrer;
    }

    @EventField
    public long getRequestSize()
    {
        return requestSize;
    }

    @EventField
    public String getRequestContentType()
    {
        return requestContentType;
    }

    @EventField
    public long getResponseSize()
    {
        return responseSize;
    }

    @EventField
    public int getResponseCode()
    {
        return responseCode;
    }

    @EventField
    public String getResponseContentType()
    {
        return responseContentType;
    }

    @EventField
    public long getTimeToDispatch()
    {
        return timeToDispatch;
    }

    @EventField
    public Long getTimeToFirstByte()
    {
        return timeToFirstByte;
    }

    @EventField
    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }

    @EventField
    public long getBeginToDispatchMillis()
    {
        return beginToDispatchMillis;
    }

    @EventField
    public long getBeginToEndMillis()
    {
        return beginToEndMillis;
    }

    @EventField
    public long getFirstToLastContentTimeInMillis()
    {
        return firstToLastContentTimeInMillis;
    }

    @EventField
    public DoubleSummaryStats getResponseContentInterarrivalStats()
    {
        return responseContentInterarrivalStats;
    }

    @EventField
    public String getProtocolVersion()
    {
        return protocolVersion;
    }
}

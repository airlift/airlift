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
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.DateTime;

import java.security.Principal;
import java.util.Enumeration;

import static io.airlift.event.client.EventField.EventFieldMapping.TIMESTAMP;
import static java.lang.Math.max;

@EventType("HttpRequest")
public class HttpRequestEvent
{
    public static HttpRequestEvent createHttpRequestEvent(Request request, Response response, TraceTokenManager traceTokenManager, long currentTimeInMillis)
    {
        String user = null;
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            user = principal.getName();
        }

        String token = null;
        if (traceTokenManager != null) {
            token = traceTokenManager.getCurrentRequestToken();
        }

        long dispatchTime = request.getTimeStamp();
        long timeToDispatch = max(dispatchTime - request.getTimeStamp(), 0);

        Long timeToFirstByte = null;
        Object firstByteTime = request.getAttribute(TimingFilter.FIRST_BYTE_TIME);
        if (firstByteTime instanceof Long) {
            Long time = (Long) firstByteTime;
            timeToFirstByte = max(time - request.getTimeStamp(), 0);
        }

        long timeToLastByte = max(currentTimeInMillis - request.getTimeStamp(), 0);

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (request.getRemoteAddr() != null) {
            builder.add(request.getRemoteAddr());
        }
        for (Enumeration<String> e = request.getHeaders("X-FORWARDED-FOR"); e != null && e.hasMoreElements(); ) {
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
            clientAddress = request.getRemoteAddr();
        }

        String requestUri = null;
        if (request.getRequestURI() != null) {
            requestUri = request.getRequestURI();
        }

        String method = request.getMethod();
        if (method != null) {
            method = method.toUpperCase();
        }

        String protocol = request.getHeader("X-FORWARDED-PROTO");
        if (protocol == null) {
            protocol = request.getScheme();
        }
        if (protocol != null) {
            protocol = protocol.toLowerCase();
        }

        return new HttpRequestEvent(
                new DateTime(request.getTimeStamp()),
                token,
                clientAddress,
                protocol,
                method,
                requestUri,
                user,
                request.getHeader("User-Agent"),
                request.getHeader("Referer"),
                request.getContentRead(),
                request.getHeader("Content-Type"),
                response.getContentCount(),
                response.getStatus(),
                response.getHeader("Content-Type"),
                timeToDispatch,
                timeToFirstByte,
                timeToLastByte
        );
    }

    private final DateTime timeStamp;
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

    public HttpRequestEvent(DateTime timeStamp,
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
            long timeToLastByte)
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
    }

    @EventField(fieldMapping = TIMESTAMP)
    public DateTime getTimeStamp()
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
}

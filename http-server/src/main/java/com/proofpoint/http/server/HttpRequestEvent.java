package com.proofpoint.http.server;

import com.google.common.base.Splitter;
import com.google.common.base.Ticker;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.proofpoint.event.client.EventField;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.DateTime;

import java.security.Principal;
import java.util.Enumeration;

import static com.proofpoint.event.client.EventField.EventFieldMapping.TIMESTAMP;
import static java.lang.Math.max;

public class HttpRequestEvent
{
    public static HttpRequestEvent createHttpRequestEvent(Request request, Response response, TraceTokenManager traceTokenManager, Ticker ticker)
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

        long dispatchTime = request.getDispatchTime();
        if (dispatchTime == 0) {
            dispatchTime = request.getTimeStamp();
        }
        long timeToDispatch = max(dispatchTime - request.getTimeStamp(), 0);

        long timeToFirstByte = 0;
        Object firstByteTime = request.getAttribute(TimingFilter.FIRST_BYTE_TIME);
        if (firstByteTime instanceof Long) {
            Long time = (Long) firstByteTime;
            timeToFirstByte = max(time - request.getTimeStamp(), 0);
        }

        long timeToLastByte = max(ticker.read() - request.getTimeStamp(), 0);

        String clientAddress = request.getRemoteAddr();
        Enumeration<String> e = request.getHeaders("X-FORWARDED-FOR");
        if (e != null) {
            String forwardedFor = Iterators.getLast(Iterators.forEnumeration(e), null);
            if (forwardedFor != null) {
                clientAddress = Iterables.getLast(Splitter.on(',').trimResults().omitEmptyStrings().split(forwardedFor), clientAddress);
            }
        }

        return new HttpRequestEvent(
                new DateTime(request.getTimeStamp()),
                token,
                clientAddress,
                request.getMethod().toUpperCase(),
                request.getUri().toString(),
                user,
                request.getHeader("User-Agent"),
                request.getContentRead(),
                response.getContentCount(),
                response.getStatus(),
                timeToDispatch,
                timeToFirstByte,
                timeToLastByte
        );
    }

    private final DateTime timeStamp;
    private final String traceToken;
    private final String clientAddress;
    private final String method;
    private final String requestUri;
    private final String user;
    private final String agent;
    private final long requestSize;
    private final long responseSize;
    private final int responseCode;
    private final long timeToDispatch;
    private final long timeToFirstByte;
    private final long timeToLastByte;

    public HttpRequestEvent(DateTime timeStamp,
            String traceToken,
            String clientAddress,
            String method,
            String requestUri,
            String user,
            String agent,
            long requestSize,
            long responseSize,
            int responseCode,
            long timeToDispatch,
            long timeToFirstByte,
            long timeToLastByte)
    {
        this.timeStamp = timeStamp;
        this.traceToken = traceToken;
        this.clientAddress = clientAddress;
        this.method = method;
        this.requestUri = requestUri;
        this.user = user;
        this.agent = agent;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
        this.responseCode = responseCode;
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
    public long getRequestSize()
    {
        return requestSize;
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
    public long getTimeToDispatch()
    {
        return timeToDispatch;
    }

    @EventField
    public long getTimeToFirstByte()
    {
        return timeToFirstByte;
    }

    @EventField
    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }
}

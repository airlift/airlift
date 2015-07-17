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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import io.airlift.event.client.InMemoryEventClient;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Test(singleThreaded = true)
public class TestDelimitedRequestLog
{
    private final DateTimeFormatter isoFormatter = new DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
            .appendTimeZoneOffset("Z", true, 2, 2)
            .toFormatter();

    private File file;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
            throws IOException
    {
        if (!file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testWriteLog()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);
        final Principal principal = mock(Principal.class);

        final long timeToFirstByte = 456;
        final long timeToLastByte = 3453;
        final long now = System.currentTimeMillis();
        final long timestamp = now - timeToLastByte;
        final String user = "martin";
        final String agent = "HttpClient 4.0";
        final String referrer = "http://www.google.com";
        final String ip = "4.4.4.4";
        final String protocol = "protocol";
        final String method = "GET";
        final long requestSize = 5432;
        final String requestContentType = "request/type";
        final long responseSize = 32311;
        final int responseCode = 200;
        final String responseContentType = "response/type";
        final HttpURI uri = new HttpURI("http://www.example.com/aaa+bbb/ccc?param=hello%20there&other=true");


        final TraceTokenManager tokenManager = new TraceTokenManager();
        InMemoryEventClient eventClient = new InMemoryEventClient();
        MockCurrentTimeMillisProvider currentTimeMillisProvider = new MockCurrentTimeMillisProvider(timestamp + timeToLastByte);
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, Long.MAX_VALUE, tokenManager, eventClient, currentTimeMillisProvider);

        when(principal.getName()).thenReturn(user);
        when(request.getTimeStamp()).thenReturn(timestamp);
        when(request.getHeader("User-Agent")).thenReturn(agent);
        when(request.getHeader("Referer")).thenReturn(referrer);
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, " + ip)));
        when(request.getProtocol()).thenReturn("unknown");
        when(request.getHeader("X-FORWARDED-PROTO")).thenReturn(protocol);
        when(request.getAttribute(TimingFilter.FIRST_BYTE_TIME)).thenReturn(timestamp + timeToFirstByte);
        when(request.getRequestURI()).thenReturn(uri.toString());
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getMethod()).thenReturn(method);
        when(request.getContentRead()).thenReturn(requestSize);
        when(request.getHeader("Content-Type")).thenReturn(requestContentType);
        when(response.getStatus()).thenReturn(responseCode);
        when(response.getContentCount()).thenReturn(responseSize);
        when(response.getHeader("Content-Type")).thenReturn(responseContentType);

        tokenManager.createAndRegisterNewRequestToken();
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);


        Assert.assertEquals(event.getTimeStamp().getMillis(), timestamp);
        Assert.assertEquals(event.getClientAddress(), ip);
        Assert.assertEquals(event.getProtocol(), protocol);
        Assert.assertEquals(event.getMethod(), method);
        Assert.assertEquals(event.getRequestUri(), uri.toString());
        Assert.assertEquals(event.getUser(), user);
        Assert.assertEquals(event.getAgent(), agent);
        Assert.assertEquals(event.getReferrer(), referrer);
        Assert.assertEquals(event.getRequestSize(), requestSize);
        Assert.assertEquals(event.getRequestContentType(), requestContentType);
        Assert.assertEquals(event.getResponseSize(), responseSize);
        Assert.assertEquals(event.getResponseCode(), responseCode);
        Assert.assertEquals(event.getResponseContentType(), responseContentType);
        Assert.assertEquals(event.getTimeToFirstByte(), (Long)timeToFirstByte);
        Assert.assertEquals(event.getTimeToLastByte(), timeToLastByte);
        Assert.assertEquals(event.getTraceToken(), tokenManager.getCurrentRequestToken());

        String actual = Files.toString(file, Charsets.UTF_8);
        String expected = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\n",
                isoFormatter.print(timestamp),
                ip,
                method,
                uri,
                user,
                agent,
                responseCode,
                requestSize,
                responseSize,
                event.getTimeToLastByte(),
                tokenManager.getCurrentRequestToken());
        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testNoXForwardedProto()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);
        final String protocol = "protocol";

        when(request.getScheme()).thenReturn("protocol");

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, Long.MAX_VALUE, null, eventClient);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        Assert.assertEquals(event.getProtocol(), protocol);
    }

    @Test
    public void testNoTimeToFirstByte()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, Long.MAX_VALUE, null, eventClient);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        Assert.assertNull(event.getTimeToFirstByte());
    }

    @Test
    public void testNoXForwardedFor()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);
        final String clientIp = "1.1.1.1";

        when(request.getRemoteAddr()).thenReturn(clientIp);

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, Long.MAX_VALUE, null, eventClient);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        Assert.assertEquals(event.getClientAddress(), clientIp);
    }

    @Test
    public void testXForwardedForSkipPrivateAddresses()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);
        final String clientIp = "1.1.1.1";

        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of(clientIp, "192.168.1.2, 172.16.0.1", "169.254.1.2, 127.1.2.3", "10.1.2.3")));

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, Long.MAX_VALUE, null, eventClient);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        Assert.assertEquals(event.getClientAddress(), clientIp);
    }
}

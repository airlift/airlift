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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import io.airlift.event.client.InMemoryEventClient;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static io.airlift.http.server.TraceTokenFilter.TRACETOKEN_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test(singleThreaded = true)
public class TestDelimitedRequestLog
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

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
    public void testTraceTokenHeader()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        TraceTokenManager tokenManager = new TraceTokenManager();
        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(
                file.getAbsolutePath(),
                1,
                256,
                Long.MAX_VALUE,
                tokenManager,
                eventClient,
                new SystemCurrentTimeMillisProvider(),
                false);
        String token = "test-trace-token";
        when(request.getHeader(TRACETOKEN_HEADER)).thenReturn(token);
        // log a request without a token set by tokenManager
        logger.log(request, response);
        // create and set a new token with tokenManager
        tokenManager.createAndRegisterNewRequestToken();
        logger.log(request, response);
        // clear the token HTTP header
        when(request.getHeader(TRACETOKEN_HEADER)).thenReturn(null);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        assertEquals(events.size(), 3);
        // first two events should have the token set from the header
        for (int i = 0; i < 2; i++) {
            assertEquals(((HttpRequestEvent) events.get(i)).getTraceToken(), token);
        }
        // last event should have the token set by the tokenManager
        assertEquals(((HttpRequestEvent) events.get(2)).getTraceToken(), tokenManager.getCurrentRequestToken());
    }

    @Test
    public void testWriteLog()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        Principal principal = mock(Principal.class);

        long timeToFirstByte = 456;
        long timeToLastByte = 3453;
        long now = System.currentTimeMillis();
        long timestamp = now - timeToLastByte;
        String user = "martin";
        String agent = "HttpClient 4.0";
        String referrer = "http://www.google.com";
        String ip = "4.4.4.4";
        String protocol = "protocol";
        String method = "GET";
        long requestSize = 5432;
        String requestContentType = "request/type";
        long responseSize = 32311;
        int responseCode = 200;
        String responseContentType = "response/type";
        HttpURI uri = new HttpURI("http://www.example.com/aaa+bbb/ccc?param=hello%20there&other=true");

        TraceTokenManager tokenManager = new TraceTokenManager();
        InMemoryEventClient eventClient = new InMemoryEventClient();
        MockCurrentTimeMillisProvider currentTimeMillisProvider = new MockCurrentTimeMillisProvider(timestamp + timeToLastByte);
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 256, Long.MAX_VALUE, tokenManager, eventClient, currentTimeMillisProvider, false);

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
        assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        assertEquals(event.getTimeStamp().toEpochMilli(), timestamp);
        assertEquals(event.getClientAddress(), ip);
        assertEquals(event.getProtocol(), protocol);
        assertEquals(event.getMethod(), method);
        assertEquals(event.getRequestUri(), uri.toString());
        assertEquals(event.getUser(), user);
        assertEquals(event.getAgent(), agent);
        assertEquals(event.getReferrer(), referrer);
        assertEquals(event.getRequestSize(), requestSize);
        assertEquals(event.getRequestContentType(), requestContentType);
        assertEquals(event.getResponseSize(), responseSize);
        assertEquals(event.getResponseCode(), responseCode);
        assertEquals(event.getResponseContentType(), responseContentType);
        assertEquals(event.getTimeToFirstByte(), (Long) timeToFirstByte);
        assertEquals(event.getTimeToLastByte(), timeToLastByte);
        assertEquals(event.getTraceToken(), tokenManager.getCurrentRequestToken());

        String actual = Files.toString(file, UTF_8);
        String expected = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\n",
                ISO_FORMATTER.format(Instant.ofEpochMilli(timestamp)),
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
        assertEquals(actual, expected);
    }

    @Test
    public void testNoXForwardedProto()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        String protocol = "protocol";

        when(request.getScheme()).thenReturn("protocol");

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 256, Long.MAX_VALUE, null, eventClient, false);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        assertEquals(event.getProtocol(), protocol);
    }

    @Test
    public void testNoTimeToFirstByte()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 256, Long.MAX_VALUE, null, eventClient, false);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        assertNull(event.getTimeToFirstByte());
    }

    @Test
    public void testNoXForwardedFor()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        String clientIp = "1.1.1.1";

        when(request.getRemoteAddr()).thenReturn(clientIp);

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 256, Long.MAX_VALUE, null, eventClient, false);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        assertEquals(event.getClientAddress(), clientIp);
    }

    @Test
    public void testXForwardedForSkipPrivateAddresses()
            throws Exception
    {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        String clientIp = "1.1.1.1";

        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of(clientIp, "192.168.1.2, 172.16.0.1", "169.254.1.2, 127.1.2.3", "10.1.2.3")));

        InMemoryEventClient eventClient = new InMemoryEventClient();
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 256, Long.MAX_VALUE, null, eventClient, false);
        logger.log(request, response);
        logger.stop();

        List<Object> events = eventClient.getEvents();
        assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);

        assertEquals(event.getClientAddress(), clientIp);
    }
}

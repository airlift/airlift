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
package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDelimitedRequestLog
{
    private File file;
    private DateTimeFormatter isoFormatter;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");

        isoFormatter = new DateTimeFormatterBuilder()
                .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
                .appendTimeZoneOffset("Z", true, 2, 2)
                .toFormatter();
    }

    @AfterMethod
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
        MockCurrentTimeMillisProvider currentTimeMillisProvider = new MockCurrentTimeMillisProvider(timestamp + timeToLastByte);
        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1, 1_000_000_000, tokenManager, currentTimeMillisProvider);

        when(principal.getName()).thenReturn(user);
        when(request.getTimeStamp()).thenReturn(timestamp);
        when(request.getHeader("User-Agent")).thenReturn(agent);
        when(request.getHeader("Referer")).thenReturn(referrer);
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, " + ip)));
        when(request.getProtocol()).thenReturn("unknown");
        when(request.getHeader("X-FORWARDED-PROTO")).thenReturn(protocol);
        when(request.getAttribute(TimingFilter.FIRST_BYTE_TIME)).thenReturn(timestamp + timeToFirstByte);
        when(request.getUri()).thenReturn(uri);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getMethod()).thenReturn(method);
        when(request.getContentRead()).thenReturn(requestSize);
        when(request.getHeader("Content-Type")).thenReturn(requestContentType);
        when(response.getStatus()).thenReturn(responseCode);
        when(response.getContentCount()).thenReturn(responseSize);
        when(response.getHeader("Content-Type")).thenReturn(responseContentType);

        tokenManager.createAndRegisterNewRequestToken();
        long currentTime = currentTimeMillisProvider.getCurrentTimeMillis();
        logger.log(request, response);
        logger.stop();

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
                currentTime - request.getTimeStamp(),
                tokenManager.getCurrentRequestToken());
        Assert.assertEquals(actual, expected);
    }
}

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
package io.airlift.rack;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.airlift.log.Logging;
import io.airlift.testing.Assertions;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Random;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;


public class TestRackServlet
{
    private Servlet servlet;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        servlet = new RackServlet(new RackServletConfig().setRackConfigPath(Resources.getResource("test/sinatra/config.ru").getPath()));
    }

    @Test
    public void testSimpleRequestWithLogging()
            throws IOException, ServletException
    {
        Logging.initialize();

        String expectedMessage = "FooBarBaz";

        OutputStream stream = new ByteArrayOutputStream();

        ch.qos.logback.classic.Logger rackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("helloworldsinatra.rb:HEAD /name-echo");
        rackLogger.setLevel(Level.ALL);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%m%n");
        encoder.setContext(context);
        encoder.start();

        OutputStreamAppender<ILoggingEvent> streamAppender = new OutputStreamAppender<ILoggingEvent>();
        streamAppender.setContext(context);
        streamAppender.setEncoder(encoder);
        streamAppender.setOutputStream(stream); // needs to happen after setEncoder()
        streamAppender.start();

        rackLogger.addAppender(streamAppender);

        assertEquals(performRequest("name=" + expectedMessage, "/name-echo", "", "GET"), expectedMessage);

        streamAppender.stop();

        Assertions.assertContains(stream.toString(), "name-echo was called with " + expectedMessage);
    }

    @Test
    public void testPostGetRequest()
            throws IOException, ServletException
    {
        String expectedMessage = "FooBarBaz";

        assertEquals(performRequest("", "/temp-store", expectedMessage, "POST"), "");
        assertEquals(performRequest("", "/temp-store", "", "GET"), expectedMessage);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullConfigThrows() throws IOException
    {
        new RackServlet(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testServletThrowsIfItCantFindTheUnderlyingRackScriptAtConstruction() throws IOException
    {
        new RackServlet(new RackServletConfig().setRackConfigPath("SomeBigFileNameThatShouldntEverExistOnTheClassPathIMeanReallyThisIsABigNameTotally.ru"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullRequestThrowsOnService() throws IOException, ServletException
    {
        servlet.service(null, mock(HttpServletResponse.class));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullResponseThrowsOnService() throws IOException, ServletException
    {
        servlet.service(mock(HttpServletRequest.class), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadServletRequestTypeThrowsIllegalArgument() throws IOException, ServletException
    {
        servlet.service(mock(ServletRequest.class), mock(HttpServletResponse.class ));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadServletReponseTypeThrowsIllegalArgument() throws IOException, ServletException
    {
        servlet.service(mock(HttpServletRequest.class), mock(ServletResponse.class ));
    }

    private String performRequest(String queryString, String path, final String postBody, String method)
            throws IOException, ServletException
    {
        HttpServletRequest request = mock(HttpServletRequest.class);

        //This provides a ServletInputStream to rack that iterates through the postBody parameter
        when(request.getInputStream()).thenReturn(new ServletInputStream()
        {
            int index = 0;
            @Override
            public int read()
                    throws IOException
            {
                if (index < postBody.length())
                    return postBody.charAt(index++);
                return -1;
            }
            @Override
            public void reset()
            {
                index = 0;
            }
        });

        when(request.getScheme()).thenReturn("http");
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getQueryString()).thenReturn(queryString);
        when(request.getServerName()).thenReturn("TestServer");
        when(request.getServerPort()).thenReturn(new Random().nextInt());
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(ImmutableList.<String>of()));

        final StringBuilder outputBuilder = new StringBuilder();
        ServletOutputStream outputStream = new ServletOutputStream()
        {
            @Override
            public void write(int i)
                    throws IOException
            {
                outputBuilder.append(new String(new int[] {i}, 0, 1));
            }
        };

        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        servlet.service(request, response);

        return outputBuilder.toString();
    }
}

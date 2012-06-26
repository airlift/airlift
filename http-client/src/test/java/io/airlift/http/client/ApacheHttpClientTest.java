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
package io.airlift.http.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import io.airlift.testing.Assertions;
import io.airlift.units.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;

public class ApacheHttpClientTest
{
    private EchoServlet servlet;
    private ApacheHttpClient httpClient;
    private Server server;
    private URI baseURI;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        servlet = new EchoServlet();
        httpClient = new ApacheHttpClient(new HttpClientConfig(),
                ImmutableSet.<HttpRequestFilter>of(new TestingRequestFilter()));

        int port;
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        finally {
            socket.close();
        }
        baseURI = new URI("http", null, "127.0.0.1", port, null, null, null);

        Server server = new Server();
        server.setSendServerVersion(false);

        SelectChannelConnector httpConnector;
        httpConnector = new SelectChannelConnector();
        httpConnector.setName("http");
        httpConnector.setPort(port);
        server.addConnector(httpConnector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        this.server = server;
        server.start();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testGetMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "GET");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
    }

    @Test
    public void testPostMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePost()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "POST");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
    }

    @Test
    public void testPutMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "PUT");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
    }

    @Test
    public void testDeleteMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = prepareDelete()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "DELETE");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 543);
    }

    @Test(expectedExceptions = UnexpectedResponseException.class)
    public void testThrowsUnexpectedResponseException()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        httpClient.execute(request, new UnexpectedResponseStatusCodeHandler(200));
    }


    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.responseStatusMessage = "message";

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String statusMessage = httpClient.execute(request, new ResponseHandler<String, Exception>()
        {
            @Override
            public Exception handleException(Request request, Exception exception)
            {
                return exception;
            }

            @Override
            public String handle(Request request, Response response)
                    throws Exception
            {
                return response.getStatusMessage();
            }
        });

        Assert.assertEquals(statusMessage, "message");
    }

    @Test
    public void testResponseHeader()
            throws Exception
    {
        servlet.responseHeaders.put("foo", "bar");
        servlet.responseHeaders.put("dupe", "first");
        servlet.responseHeaders.put("dupe", "second");

        Assert.assertEquals(servlet.responseHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.responseHeaders.get("dupe"), ImmutableList.of("first", "second"));

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        ListMultimap<String, String> headers = httpClient.execute(request, new ResponseHeadersHandler());

        Assert.assertEquals(headers.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(headers.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "");
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        servlet.responseBody = "body text";

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "body text");
    }

    @Test
    public void testErrorResponseBody()
            throws Exception
    {
        servlet.responseStatusCode = 500;
        servlet.responseBody = "body text";

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "body text");
    }

    @Test(expectedExceptions = SocketTimeoutException.class)
    public void testConnectTimeout()
            throws Exception
    {
        ServerSocket serverSocket = new ServerSocket(0, 1);
        // create one connection. The OS will auto-accept it because backlog for server socket == 1
        Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());

        HttpClientConfig config = new HttpClientConfig();
        config.setConnectTimeout(new Duration(5, TimeUnit.MILLISECONDS));
        ApacheHttpClient client = new ApacheHttpClient(config);

        Request request = prepareGet()
                .setUri(URI.create("http://localhost:" + serverSocket.getLocalPort() + "/"))
                .build();

        try {
            client.execute(request, new ResponseToStringHandler());
        }
        finally {
            clientSocket.close();
            serverSocket.close();
        }
    }

    @Test(expectedExceptions = SocketTimeoutException.class, expectedExceptionsMessageRegExp = "Read timed out")
    public void testReadTimeout()
            throws Exception
    {
        HttpClientConfig config = new HttpClientConfig()
                .setReadTimeout(new Duration(200, TimeUnit.MILLISECONDS));

        ApacheHttpClient client = new ApacheHttpClient(config);

        URI uri = URI.create(baseURI.toASCIIString() + "/?sleep=400");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        client.execute(request, new ResponseToStringHandler());
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        URI uri = URI.create(baseURI.toASCIIString() + "/?remotePort=");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        ListMultimap<String, String> headers1 = httpClient.execute(request, new ResponseHeadersHandler());
        Thread.sleep(100);
        ListMultimap<String, String> headers2 = httpClient.execute(request, new ResponseHeadersHandler());
        Thread.sleep(200);
        ListMultimap<String, String> headers3 = httpClient.execute(request, new ResponseHeadersHandler());

        Assert.assertEquals(headers1.get("remotePort").size(), 1);
        Assert.assertEquals(headers2.get("remotePort").size(), 1);
        Assert.assertEquals(headers3.get("remotePort").size(), 1);

        int port1 = Integer.parseInt(headers1.get("remotePort").get(0));
        int port2 = Integer.parseInt(headers2.get("remotePort").get(0));
        int port3 = Integer.parseInt(headers3.get("remotePort").get(0));

        Assert.assertEquals(port2, port1);
        Assert.assertEquals(port3, port1);
        Assertions.assertBetweenInclusive(port1, 1024, 65535);
    }

    private static class ResponseToStringHandler implements ResponseHandler<String, Exception>
    {
        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public String handle(Request request, Response response)
                throws Exception
        {
            return new String(ByteStreams.toByteArray(response.getInputStream()), Charsets.UTF_8);
        }
    }

    static class ResponseStatusCodeHandler implements ResponseHandler<Integer, Exception>
    {
        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public Integer handle(Request request, Response response)
                throws Exception
        {
            return response.getStatusCode();
        }
    }

    static class UnexpectedResponseStatusCodeHandler implements ResponseHandler<Integer, RuntimeException>
    {
        private int expectedStatusCode;

        UnexpectedResponseStatusCodeHandler(int expectedStatusCode)
        {
            this.expectedStatusCode = expectedStatusCode;
        }

        @Override
        public RuntimeException handleException(Request request, Exception exception)
        {
            return null;
        }

        @Override
        public Integer handle(Request request, Response response)
                throws RuntimeException
        {
            if (response.getStatusCode() != expectedStatusCode)
            {
                throw new UnexpectedResponseException(request, response);
            }
            return response.getStatusCode();
        }
    }


    private static class ResponseHeadersHandler
            implements ResponseHandler<ListMultimap<String, String>, Exception>
    {
        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public ListMultimap<String, String> handle(Request request, Response response)
                throws Exception
        {
            return response.getHeaders();
        }
    }
}

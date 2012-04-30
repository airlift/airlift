package com.proofpoint.http.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.proofpoint.testing.Assertions;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

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
        httpClient = new ApacheHttpClient(new HttpClientConfig());

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
        Request request = RequestBuilder.prepareGet()
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
    }

    @Test
    public void testPostMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = RequestBuilder.preparePost()
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
    }

    @Test
    public void testPutMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = RequestBuilder.preparePut()
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
    }

    @Test
    public void testDeleteMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = RequestBuilder.prepareDelete()
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
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = RequestBuilder.prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 543);
    }

    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.responseStatusMessage = "message";

        Request request = RequestBuilder.prepareGet()
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

        Request request = RequestBuilder.prepareGet()
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
        Request request = RequestBuilder.prepareGet()
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

        Request request = RequestBuilder.prepareGet()
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

        Request request = RequestBuilder.prepareGet()
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

        Request request = RequestBuilder.prepareGet()
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
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        client.execute(request, new ResponseToStringHandler());
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        URI uri = URI.create(baseURI.toASCIIString() + "/?remotePort=");
        Request request = RequestBuilder.prepareGet()
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

    private static final class EchoServlet extends HttpServlet
    {
        private String requestMethod;
        private URI requestUri;
        private final ListMultimap<String, String> requestHeaders = ArrayListMultimap.create();

        private int responseStatusCode = 200;
        private String responseStatusMessage;
        private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        public String responseBody;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            requestMethod = request.getMethod();
            requestUri = URI.create(HttpUtils.getRequestURL(request).toString());

            requestHeaders.clear();
            for (String name : Collections.list(request.getHeaderNames())) {
                requestHeaders.putAll(name, Collections.list(request.getHeaders(name)));
            }

            if (responseStatusMessage != null) {
                response.sendError(responseStatusCode, responseStatusMessage);
            }
            else {
                response.setStatus(responseStatusCode);
            }
            for (Entry<String, String> entry : responseHeaders.entries()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }

            try {
                if (request.getParameter("sleep") != null) {
                    Thread.sleep(Long.parseLong(request.getParameter("sleep")));
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (request.getParameter("remotePort") != null) {
                response.addHeader("remotePort", String.valueOf(request.getRemotePort()));
            }

            if (responseBody != null) {
                response.getOutputStream().write(responseBody.getBytes(Charsets.UTF_8));
            }
        }
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

    private static class ResponseStatusCodeHandler implements ResponseHandler<Integer, Exception>
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

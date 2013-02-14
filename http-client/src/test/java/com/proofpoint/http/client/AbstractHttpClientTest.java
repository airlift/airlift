package com.proofpoint.http.client;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.proofpoint.testing.Assertions;
import com.proofpoint.units.Duration;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.units.Duration.nanosSince;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractHttpClientTest
{
    protected EchoServlet servlet;
    protected Server server;
    protected URI baseURI;

    public abstract <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    public abstract <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    @BeforeMethod
    public void abstractSetup()
            throws Exception
    {
        servlet = new EchoServlet();

        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
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
    public void abstractTeardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test(enabled = false, description = "This takes over a minute to run")
    public void test100kGets()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        for (int i = 0; i < 100_000; i++) {
            try {
                int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
                Assert.assertEquals(statusCode, 200);
            }
            catch (Exception e) {
                throw new Exception("Error on request " + i, e);
            }
        }
    }

    @Test(expectedExceptions = SocketTimeoutException.class)
    public void testConnectTimeout()
            throws Exception
    {
        ServerSocket serverSocket = new ServerSocket(0, 1);
        // create one connection. The OS will auto-accept it because backlog for server socket == 1

        HttpClientConfig config = new HttpClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/"))
                .build();

        try (Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort())) {
            executeRequest(config, request, new ResponseToStringHandler());
        }
        finally {
            serverSocket.close();
        }
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

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "DELETE");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
//        Assert.assertEquals(servlet.requestHeaders.get(HTTP.TRANSFER_ENCODING), Collections.emptyList());
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

        String body = executeRequest(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "body text");
    }

    @Test
    public void testGetMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "GET");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
//        Assert.assertEquals(servlet.requestHeaders.get(HTTP.TRANSFER_ENCODING), Collections.emptyList());
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        URI uri = URI.create(baseURI.toASCIIString() + "/?remotePort=");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        ListMultimap<String, String> headers1 = executeRequest(request, new ResponseHeadersHandler());
        Thread.sleep(1000);
        ListMultimap<String, String> headers2 = executeRequest(request, new ResponseHeadersHandler());
        Thread.sleep(1000);
        ListMultimap<String, String> headers3 = executeRequest(request, new ResponseHeadersHandler());

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

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "POST");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
//        Assert.assertEquals(servlet.requestHeaders.get(HTTP.TRANSFER_ENCODING), Collections.emptyList());
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

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "PUT");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
//        Assert.assertEquals(servlet.requestHeaders.get(HTTP.TRANSFER_ENCODING), Collections.emptyList());
    }

    @Test
    public void testPutMethodWithBodyGenerator()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(new byte[0]))
                .build();

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "PUT");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
        Assert.assertEquals(servlet.requestHeaders.get("x-custom-filter"), ImmutableList.of("customvalue"));
//        Assert.assertEquals(servlet.requestHeaders.get(HTTP.TRANSFER_ENCODING), ImmutableList.of(HTTP.CHUNK_CODING));
    }

    @Test(expectedExceptions = SocketTimeoutException.class)
    public void testReadTimeout()
            throws Exception
    {
        HttpClientConfig config = new HttpClientConfig()
                .setReadTimeout(new Duration(99, MILLISECONDS));

        URI uri = URI.create(baseURI.toASCIIString() + "/?sleep=1000");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(config, request, new ResponseToStringHandler());
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        servlet.responseBody = "body text";

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "body text");
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, new ResponseToStringHandler());
        Assert.assertEquals(body, "");
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

        ListMultimap<String, String> headers = executeRequest(request, new ResponseHeadersHandler());

        Assert.assertEquals(headers.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(headers.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = executeRequest(request, new ResponseStatusCodeHandler());
        Assert.assertEquals(statusCode, 543);
    }

    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.responseStatusMessage = "message";

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String statusMessage = executeRequest(request, new ResponseHandler<String, Exception>()
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

    @Test(expectedExceptions = UnexpectedResponseException.class)
    public void testThrowsUnexpectedResponseException()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new UnexpectedResponseStatusCodeHandler(200));
    }

    private ExecutorService executor;

    @BeforeClass
    public void setup()
            throws Exception
    {
        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("test-%s").build());
    }

    @AfterClass
    public void tearDown()
            throws Exception
    {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectNoRead()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(0, null, false)) {
            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectNoReadClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(0, null, true)) {

            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = IOException.class)
    public void testConnectReadIncomplete()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(10, null, false)) {
            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = IOException.class)
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(10, null, true)) {
            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectReadRequestClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(Long.MAX_VALUE, null, true)) {
            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = Exception.class)
    public void testConnectReadRequestWriteJunkHangup()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(10, "THIS\nIS\nJUNK\n\n".getBytes(), false)) {
            HttpClientConfig config = new HttpClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setReadTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    private void executeRequest(FakeServer fakeServer, HttpClientConfig config)
            throws Exception
    {
        // kick the fake server
        executor.execute(fakeServer);

        // timing based check to assure we don't hang
        long start = System.nanoTime();
        try {
            Request request = prepareGet()
                    .setUri(fakeServer.getUri())
                    .build();
            executeRequest(config, request, new ResponseToStringHandler());
        }
        finally {
            Assertions.assertLessThan(nanosSince(start), new Duration(1, SECONDS), "Expected request to finish quickly");
        }
    }

    private static class FakeServer
            implements Closeable, Runnable
    {
        private final ServerSocket serverSocket;
        private final long readBytes;
        private final byte[] writeBuffer;
        private final boolean closeConnectionImmediately;
        private final AtomicReference<Socket> connectionSocket = new AtomicReference<>();


        private FakeServer(long readBytes, byte[] writeBuffer, boolean closeConnectionImmediately)
                throws Exception
        {
            this.writeBuffer = writeBuffer;
            this.readBytes = readBytes;
            this.serverSocket = new ServerSocket(0);
            this.closeConnectionImmediately = closeConnectionImmediately;
        }

        public URI getUri()
        {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/");
        }

        @Override
        public void run()
        {
            try {
                Socket connectionSocket = serverSocket.accept();
                this.connectionSocket.set(connectionSocket);
                if (readBytes > 0) {
                    connectionSocket.setSoTimeout(5);
                    long bytesRead = 0;
                    try {
                        InputStream inputStream = connectionSocket.getInputStream();
                        while (bytesRead < readBytes) {
                            inputStream.read();
                            bytesRead++;
                        }
                    }
                    catch (SocketTimeoutException ignored) {
                    }
                }
                if (writeBuffer != null) {
                    connectionSocket.getOutputStream().write(writeBuffer);
                }
                // todo sleep here maybe
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
            finally {
                if (closeConnectionImmediately) {
                    Closeables.closeQuietly(connectionSocket.get());
                }
            }
        }

        @Override
        public void close()
                throws IOException
        {
            Closeables.closeQuietly(connectionSocket.get());
            serverSocket.close();
        }
    }

    public static class ResponseToStringHandler
            implements ResponseHandler<String, Exception>
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

    static class ResponseStatusCodeHandler
            implements ResponseHandler<Integer, Exception>
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

    static class UnexpectedResponseStatusCodeHandler
            implements ResponseHandler<Integer, RuntimeException>
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
            if (response.getStatusCode() != expectedStatusCode) {
                throw new UnexpectedResponseException(request, response);
            }
            return response.getStatusCode();
        }
    }

    public static class ResponseHeadersHandler
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

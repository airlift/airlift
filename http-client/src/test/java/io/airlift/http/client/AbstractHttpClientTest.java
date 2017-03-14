package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.log.Logging;
import io.airlift.testing.Closeables;
import io.airlift.units.Duration;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.base.Throwables.propagateIfPossible;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.concurrent.Threads.threadsNamed;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.testing.Assertions.assertBetweenInclusive;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertLessThan;
import static io.airlift.testing.Closeables.closeQuietly;
import static io.airlift.units.Duration.nanosSince;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public abstract class AbstractHttpClientTest
{
    protected EchoServlet servlet;
    protected Server server;
    protected URI baseURI;
    private String scheme = "http";
    private String host = "127.0.0.1";
    private String keystore;

    protected AbstractHttpClientTest()
    {
    }

    protected AbstractHttpClientTest(String host, String keystore)
    {
        scheme = "https";
        this.host = host;
        this.keystore = keystore;
    }

    protected abstract HttpClientConfig createClientConfig();

    public abstract <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    public abstract <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void abstractSetup()
            throws Exception
    {
        servlet = new EchoServlet();

        Server server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        ServerConnector connector;
        if (keystore != null) {
            httpConfiguration.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory sslContextFactory = new SslContextFactory(keystore);
            sslContextFactory.setKeyStorePassword("changeit");
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

            connector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(httpConfiguration));
        }
        else {
            HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
            HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);

            connector = new ServerConnector(server, http1, http2c);
        }

        connector.setIdleTimeout(30000);
        connector.setName(scheme);

        server.addConnector(connector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setGzipHandler(new GzipHandler());
        context.addServlet(servletHolder, "/*");
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        this.server = server;
        server.start();

        baseURI = new URI(scheme, null, host, connector.getLocalPort(), null, null, null);
    }

    @AfterMethod(alwaysRun = true)
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
                int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
                assertEquals(statusCode, 200);
            }
            catch (Exception e) {
                throw new Exception("Error on request " + i, e);
            }
        }
    }

    @Test(timeOut = 5000)
    public void testConnectTimeout()
            throws Exception
    {
        try (BackloggedServer server = new BackloggedServer()) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, MILLISECONDS));
            config.setIdleTimeout(new Duration(2, SECONDS));

            Request request = prepareGet()
                    .setUri(new URI(scheme, null, host, server.getPort(), "/", null, null))
                    .build();

            long start = System.nanoTime();
            try {
                executeRequest(config, request, new CaptureExceptionResponseHandler());
                fail("expected exception");
            }
            catch (CapturedException e) {
                Throwable t = e.getCause();
                if (!isConnectTimeout(t)) {
                    fail(format("unexpected exception: [%s]", getStackTraceAsString(t)));
                }
                assertLessThan(nanosSince(start), new Duration(300, MILLISECONDS));
            }
        }
    }

    @Test(expectedExceptions = ConnectException.class)
    public void testConnectionRefused()
            throws Exception
    {
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        executeExceptionRequest(config, request);
    }

    @Test
    public void testConnectionRefusedWithDefaultingResponseExceptionHandler()
            throws Exception
    {
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        Object expected = new Object();
        assertEquals(executeRequest(config, request, new DefaultOnExceptionResponseHandler(expected)), expected);
    }

    @Test(expectedExceptions = {UnknownHostException.class, UnresolvedAddressException.class}, timeOut = 10000)
    public void testUnresolvableHost()
            throws Exception
    {
        String invalidHost = "nonexistent.invalid";
        assertUnknownHost(invalidHost);

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, SECONDS));

        Request request = prepareGet()
                .setUri(URI.create("http://" + invalidHost))
                .build();

        executeExceptionRequest(config, request);
    }


    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPort()
            throws Exception
    {
        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, 70_000, "/", null, null))
                .build();

        executeExceptionRequest(config, request);
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

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "DELETE");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testErrorResponseBody()
            throws Exception
    {
        servlet.setResponseStatusCode(500);
        servlet.setResponseBody("body text");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        StringResponse response = executeRequest(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), 500);
        assertEquals(response.getBody(), "body text");
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

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "GET");
        if (servlet.getRequestUri().toString().endsWith("=")) {
            // todo jetty client rewrites the uri string for some reason
            assertEquals(servlet.getRequestUri(), new URI(uri + "="));
        }
        else {
            assertEquals(servlet.getRequestUri(), uri);
        }
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testResponseHeadersCaseInsensitive()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        Response response = executeRequest(request, new PassThroughResponseHandler());

        assertNotNull(response.getHeader("date"));
        assertNotNull(response.getHeader("DATE"));

        assertEquals(response.getHeaders("date").size(), 1);
        assertEquals(response.getHeaders("DATE").size(), 1);
    }

    @Test
    public void testQuotedSpace()
        throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query=ab%20cd");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "GET");
        assertEquals(servlet.getRequestUri(), uri);
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        URI uri = URI.create(baseURI.toASCIIString() + "/?remotePort=");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        StatusResponse response1 = executeRequest(request, createStatusResponseHandler());
        Thread.sleep(1000);
        StatusResponse response2 = executeRequest(request, createStatusResponseHandler());
        Thread.sleep(1000);
        StatusResponse response3 = executeRequest(request, createStatusResponseHandler());

        assertNotNull(response1.getHeader("remotePort"));
        assertNotNull(response2.getHeader("remotePort"));
        assertNotNull(response3.getHeader("remotePort"));

        int port1 = Integer.parseInt(response1.getHeader("remotePort"));
        int port2 = Integer.parseInt(response2.getHeader("remotePort"));
        int port3 = Integer.parseInt(response3.getHeader("remotePort"));

        assertEquals(port2, port1);
        assertEquals(port3, port1);
        assertBetweenInclusive(port1, 1024, 65535);
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

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "POST");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
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

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testPutMethodWithStaticBodyGenerator()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        byte[] body = {1, 2, 5};
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(body))
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), body);
    }

    @Test
    public void testPutMethodWithDynamicBodyGenerator()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodyGenerator(out -> {
                    out.write(1);
                    out.write(new byte[] {2, 5});
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), new byte[] {1, 2, 5});
    }

    @Test
    public void testPutMethodWithFileBodyGenerator()
            throws Exception
    {
        byte[] contents = "hello world".getBytes(UTF_8);
        File testFile = File.createTempFile("test", null);
        Files.write(testFile.toPath(), contents);

        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader(CONTENT_TYPE, "x-test")
                .setBodyGenerator(new FileBodyGenerator(testFile.toPath()))
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders(CONTENT_TYPE), ImmutableList.of("x-test"));
        assertEquals(servlet.getRequestHeaders(CONTENT_LENGTH), ImmutableList.of(String.valueOf(contents.length)));
        assertEquals(servlet.getRequestBytes(), contents);

        assertTrue(testFile.delete());
    }

    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testReadTimeout()
            throws Exception
    {
        HttpClientConfig config = createClientConfig()
                .setIdleTimeout(new Duration(500, MILLISECONDS));

        URI uri = URI.create(baseURI.toASCIIString() + "/?sleep=1000");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(config, request, new ExceptionResponseHandler());
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        servlet.setResponseBody("body text");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        StringResponse response = executeRequest(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(), "body text");
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "");
    }

    @Test
    public void testResponseHeader()
            throws Exception
    {
        servlet.addResponseHeader("foo", "bar");
        servlet.addResponseHeader("dupe", "first");
        servlet.addResponseHeader("dupe", "second");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        StatusResponse response = executeRequest(request, createStatusResponseHandler());

        assertEquals(response.getHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(response.getHeaders("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.setResponseStatusCode(543);
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 543);
    }

    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.setResponseStatusMessage("message");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String statusMessage = executeRequest(request, createStatusResponseHandler()).getStatusMessage();

        if (createClientConfig().isHttp2Enabled()) {
            // reason phrases are not supported in HTTP/2
            assertNull(statusMessage);
        }
        else {
            assertEquals(statusMessage, "message");
        }
    }

    @Test(expectedExceptions = UnexpectedResponseException.class)
    public void testThrowsUnexpectedResponseException()
            throws Exception
    {
        servlet.setResponseStatusCode(543);
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new UnexpectedResponseStatusCodeHandler(200));
    }

    @Test
    public void testCompressionIsDisabled()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "");
        assertFalse(servlet.getRequestHeaders().containsKey(HeaderName.of(ACCEPT_ENCODING)));

        String json = "{\"foo\":\"bar\",\"hello\":\"world\"}";
        assertGreaterThanOrEqual(json.length(), GzipHandler.DEFAULT_MIN_GZIP_SIZE);

        servlet.setResponseBody(json);
        servlet.addResponseHeader(CONTENT_TYPE, "application/json");

        StringResponse response = executeRequest(request, createStringResponseHandler());
        assertEquals(response.getHeader(CONTENT_TYPE), "application/json");
        assertEquals(response.getBody(), json);
    }

    private ExecutorService executor;

    @BeforeClass
    public final void setUp()
            throws Exception
    {
        executor = Executors.newCachedThreadPool(threadsNamed("test-%s"));
    }

    @AfterClass(alwaysRun = true)
    public final void tearDown()
            throws Exception
    {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectNoRead()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectNoReadClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, true)) {

            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectReadIncomplete()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(500, MILLISECONDS));
            config.setIdleTimeout(new Duration(500, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectReadRequestClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, Long.MAX_VALUE, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = Exception.class)
    public void testConnectReadRequestWriteJunkHangup()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, "THIS\nIS\nJUNK\n\n".getBytes(), false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = CustomError.class)
    public void testHandlesUndeclaredThrowable()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new ThrowErrorResponseHandler());
    }

    private void executeExceptionRequest(HttpClientConfig config, Request request)
            throws Exception
    {
        try {
            executeRequest(config, request, new CaptureExceptionResponseHandler());
            fail("expected exception");
        }
        catch (CapturedException e) {
            propagateIfPossible(e.getCause(), Exception.class);
            throw new RuntimeException(e.getCause());
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
            executeRequest(config, request, new ExceptionResponseHandler());
        }
        finally {
            assertLessThan(nanosSince(start), new Duration(1, SECONDS), "Expected request to finish quickly");
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
        private final String scheme;
        private final String host;


        private FakeServer(String scheme, String host, long readBytes, byte[] writeBuffer, boolean closeConnectionImmediately)
                throws Exception
        {
            this.scheme = scheme;
            this.host = host;
            this.writeBuffer = writeBuffer;
            this.readBytes = readBytes;
            this.serverSocket = new ServerSocket(0);
            this.closeConnectionImmediately = closeConnectionImmediately;
        }

        public URI getUri()
        {
            try {
                return new URI(scheme, null, host, serverSocket.getLocalPort(), "/", null, null);
            }
            catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
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
                throw new UncheckedIOException(e);
            }
            finally {
                if (closeConnectionImmediately) {
                    closeQuietly(connectionSocket.get());
                }
            }
        }

        @Override
        public void close()
                throws IOException
        {
            closeQuietly(connectionSocket.get());
            serverSocket.close();
        }
    }

    public static class ExceptionResponseHandler
            implements ResponseHandler<Void, Exception>
    {
        @Override
        public Void handleException(Request request, Exception exception)
                throws Exception
        {
            throw exception;
        }

        @Override
        public Void handle(Request request, Response response)
                throws Exception
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class PassThroughResponseHandler
            implements ResponseHandler<Response, RuntimeException>
    {
        @Override
        public Response handleException(Request request, Exception exception)
        {
            throw ResponseHandlerUtils.propagate(request, exception);
        }

        @Override
        public Response handle(Request request, Response response)
        {
            return response;
        }
    }

    private static class UnexpectedResponseStatusCodeHandler
            implements ResponseHandler<Integer, RuntimeException>
    {
        private final int expectedStatusCode;

        UnexpectedResponseStatusCodeHandler(int expectedStatusCode)
        {
            this.expectedStatusCode = expectedStatusCode;
        }

        @Override
        public Integer handleException(Request request, Exception exception)
        {
            throw ResponseHandlerUtils.propagate(request, exception);
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

    public static class CaptureExceptionResponseHandler
            implements ResponseHandler<String, CapturedException>
    {
        @Override
        public String handleException(Request request, Exception exception)
                throws CapturedException
        {
            throw new CapturedException(exception);
        }

        @Override
        public String handle(Request request, Response response)
        {
            return null;
        }
    }

    public static class ThrowErrorResponseHandler
            implements ResponseHandler<String, Exception>
    {
        @Override
        public String handleException(Request request, Exception exception)
        {
            throw new UnsupportedOperationException("not yet implemented", exception);
        }

        @Override
        public String handle(Request request, Response response)
        {
            throw new CustomError();
        }
    }

    private static class CustomError
            extends Error {
    }

    public static class CapturedException
            extends Exception
    {
        public CapturedException(Exception exception)
        {
            super(exception);
        }
    }

    private class DefaultOnExceptionResponseHandler
            implements ResponseHandler<Object, RuntimeException>
    {
        private final Object defaultObject;

        public DefaultOnExceptionResponseHandler(Object defaultObject)
        {
            this.defaultObject = defaultObject;
        }

        @Override
        public Object handleException(Request request, Exception exception)
                throws RuntimeException
        {
            return defaultObject;
        }

        @Override
        public Object handle(Request request, Response response)
                throws RuntimeException
        {
            throw new UnsupportedOperationException();
        }
    }

    private static int findUnusedPort()
            throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SuppressWarnings("SocketOpenedButNotSafelyClosed")
    private static class BackloggedServer
            implements Closeable
    {
        private final List<Socket> clientSockets = new ArrayList<>();
        private final ServerSocket serverSocket;
        private final SocketAddress localSocketAddress;

        private BackloggedServer()
                throws IOException
        {
            this.serverSocket = new ServerSocket(0, 1);
            localSocketAddress = serverSocket.getLocalSocketAddress();

            // some systems like Linux have a large minimum backlog
            int i = 0;
            while (i <= 40) {
                if (!connect()) {
                    return;
                }
                i++;
            }
            throw new SkipException(format("socket backlog is too large (%s connections accepted)", i));
        }

        @Override
        public void close()
        {
            clientSockets.forEach(Closeables::closeQuietly);
            closeQuietly(serverSocket);
        }

        private int getPort()
        {
            return serverSocket.getLocalPort();
        }

        private boolean connect()
                throws IOException
        {
            Socket socket = new Socket();
            clientSockets.add(socket);

            try {
                socket.connect(localSocketAddress, 5);
                return true;
            }
            catch (IOException e) {
                if (isConnectTimeout(e)) {
                    return false;
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void assertUnknownHost(String host)
    {
        try {
            InetAddress.getByName(host);
            fail("Expected UnknownHostException for host " + host);
        }
        catch (UnknownHostException e) {
            // expected
        }
    }

    private static boolean isConnectTimeout(Throwable t)
    {
        // Linux refuses connections immediately rather than queuing them
        return (t instanceof SocketTimeoutException) || (t instanceof SocketException);
    }

    public static <T, E extends Exception> T executeAsync(JettyHttpClient client, Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        HttpResponseFuture<T> future = null;
        try {
            future = client.executeAsync(request, responseHandler);
        }
        catch (Exception e) {
            fail("Unexpected exception", e);
        }

        try {
            return future.get();
        }
        catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (ExecutionException e) {
            throwIfUnchecked(e.getCause());

            if (e.getCause() instanceof Exception) {
                // the HTTP client and ResponseHandler interface enforces this
                throw AbstractHttpClientTest.<E>castThrowable(e.getCause());
            }

            // e.getCause() is some direct subclass of throwable
            throw new RuntimeException(e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> E castThrowable(Throwable t)
    {
        return (E) t;
    }
}

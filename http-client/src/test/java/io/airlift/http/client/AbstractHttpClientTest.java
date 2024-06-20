package io.airlift.http.client;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.log.Logging;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;

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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static io.airlift.concurrent.Threads.threadsNamed;
import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StreamingBodyGenerator.streamingBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.testing.Assertions.assertBetweenInclusive;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertLessThan;
import static io.airlift.units.Duration.nanosSince;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public abstract class AbstractHttpClientTest
{
    public static final String LARGE_CONTENT = IntStream.range(0, 10_000_000).mapToObj(ignore -> "hello").collect(Collectors.joining());

    protected EchoServlet servlet;
    protected TestingHttpServer server;
    protected URI baseURI;
    private String scheme = "http";
    private String host = "127.0.0.1";
    protected String keystore;
    protected final Multiset<Integer> statusCounts = HashMultiset.create();

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

    protected static Request upgradeRequest(Request request, HttpVersion version)
    {
        return fromRequest(request)
                .setVersion(version)
                .build();
    }

    @BeforeAll
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeEach
    public void abstractSetup()
            throws Exception
    {
        servlet = new EchoServlet();

        TestingHttpServer server = new TestingHttpServer(Optional.ofNullable(keystore), servlet);

        baseURI = new URI(scheme, null, server.getHostAndPort().getHost(), server.getHostAndPort().getPort(), null, null, null);

        statusCounts.clear();
    }

    @AfterEach
    public void abstractTeardown()
            throws Exception
    {
        if (server != null) {
            server.close();
        }
    }

    @Disabled("This takes over a minute to run")
    @Test
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
                assertThat(statusCode).isEqualTo(200);
            }
            catch (Exception e) {
                throw new Exception("Error on request " + i, e);
            }
        }
    }

    @Test
    @Timeout(5)
    public void testConnectTimeout()
            throws Exception
    {
        doTestConnectTimeout(false);
    }

    protected void doTestConnectTimeout(boolean useIdleTimeout)
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
                if (!(isConnectTimeout(t) || (t instanceof ClosedChannelException) || (t instanceof TimeoutException))) {
                    fail(format("unexpected exception: [%s]", getStackTraceAsString(t)));
                }
                Duration maxDuration = useIdleTimeout ? config.getIdleTimeout() : config.getConnectTimeout();
                maxDuration = new Duration(maxDuration.toMillis() + 300, MILLISECONDS);
                assertLessThan(nanosSince(start), maxDuration);
            }
        }
    }

    @Test
    public void testConnectionRefused()
            throws Exception
    {
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, SECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        assertThatThrownBy(() -> executeExceptionRequest(config, request))
                .isInstanceOfAny(ConnectException.class, IOException.class, HttpResponseException.class);
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
        assertThat(executeRequest(config, request, new DefaultOnExceptionResponseHandler(expected))).isEqualTo(expected);
    }

    @Test
    @Timeout(10)
    public void testUnresolvableHost()
    {
        String invalidHost = "nonexistent.invalid";
        assertUnknownHost(invalidHost);

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, SECONDS));

        Request request = prepareGet()
                .setUri(URI.create("http://" + invalidHost))
                .build();

        assertThatThrownBy(() -> executeExceptionRequest(config, request))
                .isInstanceOfAny(UnknownHostException.class, UnresolvedAddressException.class, IOException.class);
    }

    @Test
    public void testBadPort()
            throws Exception
    {
        HttpClientConfig config = createClientConfig();

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, 70_000, "/", null, null))
                .build();

        assertThatThrownBy(() -> executeExceptionRequest(config, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageMatching(".*port out of range.*");
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("DELETE");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(statusCounts.count(200)).isEqualTo(1);
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
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo("body text");
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("GET");
        if (servlet.getRequestUri().toString().endsWith("=")) {
            // todo jetty client rewrites the uri string for some reason
            assertThat(servlet.getRequestUri()).isEqualTo(new URI(uri + "="));
        }
        else {
            assertThat(servlet.getRequestUri()).isEqualTo(uri);
        }
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(statusCounts.count(200)).isEqualTo(1);
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

        assertThat(response.getHeader("date")).isNotNull();
        assertThat(response.getHeader("DATE")).isNotNull();

        assertThat(response.getHeaders("date").size()).isEqualTo(1);
        assertThat(response.getHeaders("DATE").size()).isEqualTo(1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("GET");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
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

        assertThat(response1.getHeader("remotePort")).isNotNull();
        assertThat(response2.getHeader("remotePort")).isNotNull();
        assertThat(response3.getHeader("remotePort")).isNotNull();

        int port1 = Integer.parseInt(response1.getHeader("remotePort"));
        int port2 = Integer.parseInt(response2.getHeader("remotePort"));
        int port3 = Integer.parseInt(response3.getHeader("remotePort"));

        assertThat(port2).isEqualTo(port1);
        assertThat(port3).isEqualTo(port1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("POST");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(statusCounts.count(200)).isEqualTo(1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("PUT");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(statusCounts.count(200)).isEqualTo(1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("PUT");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(servlet.getRequestBytes()).isEqualTo(body);
        assertThat(statusCounts.count(200)).isEqualTo(1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("PUT");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(servlet.getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        assertThat(servlet.getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
        assertThat(servlet.getRequestBytes()).isEqualTo(new byte[] {1, 2, 5});
        assertThat(statusCounts.count(200)).isEqualTo(1);
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
        assertThat(statusCode).isEqualTo(200);
        assertThat(servlet.getRequestMethod()).isEqualTo("PUT");
        assertThat(servlet.getRequestUri()).isEqualTo(uri);
        assertThat(servlet.getRequestHeaders(CONTENT_TYPE)).isEqualTo(ImmutableList.of("x-test"));
        assertThat(servlet.getRequestHeaders(CONTENT_LENGTH)).isEqualTo(ImmutableList.of(String.valueOf(contents.length)));
        assertThat(servlet.getRequestBytes()).isEqualTo(contents);

        assertThat(testFile.delete()).isTrue();
    }

    @Test
    public void testReadTimeout()
    {
        HttpClientConfig config = createClientConfig()
                .setIdleTimeout(new Duration(500, MILLISECONDS));

        URI uri = URI.create(baseURI.toASCIIString() + "/?sleep=1000");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        assertThatThrownBy(() -> executeRequest(config, request, new ExceptionResponseHandler()))
                .isInstanceOfAny(IOException.class, TimeoutException.class);
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
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("body text");
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertThat(body).isEqualTo("");
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

        assertThat(response.getHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
        assertThat(response.getHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
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
        assertThat(statusCode).isEqualTo(543);
    }

    @Test
    public void testRequestHeaders()
            throws Exception
    {
        String basic = "Basic dGVzdDphYmM=";
        String bearer = "Bearer testxyz";

        Request request = prepareGet()
                .setUri(baseURI)
                .addHeader("X-Test", "xtest1")
                .addHeader("X-Test", "xtest2")
                .setHeader(USER_AGENT, "testagent")
                .addHeader(AUTHORIZATION, basic)
                .addHeader(AUTHORIZATION, bearer)
                .build();

        StatusResponse response = executeRequest(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(servlet.getRequestHeaders("X-Test")).containsExactly("xtest1", "xtest2");
        assertThat(servlet.getRequestHeaders(USER_AGENT)).containsExactly("testagent");
        assertThat(servlet.getRequestHeaders(AUTHORIZATION)).containsExactly(basic, bearer);
    }

    @Test
    public void testRedirectRequestHeaders()
            throws Exception
    {
        String basic = "Basic dGVzdDphYmM=";
        String bearer = "Bearer testxyz";

        Request request = prepareGet()
                .setUri(URI.create(baseURI.toASCIIString() + "/?redirect=/redirect"))
                .addHeader("X-Test", "xtest1")
                .addHeader("X-Test", "xtest2")
                .setHeader(USER_AGENT, "testagent")
                .addHeader(AUTHORIZATION, basic)
                .addHeader(AUTHORIZATION, bearer)
                .build();

        StatusResponse response = executeRequest(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(servlet.getRequestUri()).isEqualTo(URI.create(baseURI.toASCIIString() + "/redirect"));
        assertThat(servlet.getRequestHeaders("X-Test")).containsExactly("xtest1", "xtest2");
        assertThat(servlet.getRequestHeaders(USER_AGENT)).containsExactly("testagent");
        assertThat(servlet.getRequestHeaders(AUTHORIZATION)).isEmpty();

        request = Request.Builder.fromRequest(request)
                .setPreserveAuthorizationOnRedirect(true)
                .build();

        response = executeRequest(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(servlet.getRequestUri()).isEqualTo(URI.create(baseURI.toASCIIString() + "/redirect"));
        assertThat(servlet.getRequestHeaders("X-Test")).containsExactly("xtest1", "xtest2");
        assertThat(servlet.getRequestHeaders(USER_AGENT)).containsExactly("testagent");
        assertThat(servlet.getRequestHeaders(AUTHORIZATION)).containsExactly(basic, bearer);
    }

    @Test
    public void testFollowRedirects()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(URI.create(baseURI.toASCIIString() + "/test?redirect=/redirect"))
                .build();

        StatusResponse response = executeRequest(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeader(LOCATION)).isNull();
        assertThat(servlet.getRequestUri()).isEqualTo(URI.create(baseURI.toASCIIString() + "/redirect"));

        request = Request.Builder.fromRequest(request)
                .setFollowRedirects(false)
                .build();

        response = executeRequest(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(302);
        assertThat(response.getHeader(LOCATION)).isEqualTo("/redirect");
        assertThat(servlet.getRequestUri()).isEqualTo(request.getUri());
    }

    @Test
    public void testThrowsUnexpectedResponseException()
    {
        servlet.setResponseStatusCode(543);
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        assertThatThrownBy(() -> executeRequest(request, new UnexpectedResponseStatusCodeHandler(200)))
                .isInstanceOf(UnexpectedResponseException.class);
    }

    @Test
    public void testCompressionIsDisabled()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertThat(body).isEqualTo("");
        assertThat(servlet.getRequestHeaders().containsKey(HeaderName.of(ACCEPT_ENCODING))).isFalse();

        String json = "{\"fuite\":\"apple\",\"hello\":\"world\"}";
        assertGreaterThanOrEqual(json.length(), GzipHandler.DEFAULT_MIN_GZIP_SIZE);

        servlet.setResponseBody(json);
        servlet.addResponseHeader(CONTENT_TYPE, "application/json");

        StringResponse response = executeRequest(request, createStringResponseHandler());
        assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getBody()).isEqualTo(json);
    }

    private ExecutorService executor;

    @BeforeAll
    public final void setUp()
    {
        executor = Executors.newCachedThreadPool(threadsNamed("test-%s"));
    }

    @AfterAll
    public final void tearDown()
    {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testConnectNoRead()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(IOException.class, TimeoutException.class);
        }
    }

    @Test
    public void testConnectNoReadClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(IOException.class, IllegalStateException.class);
        }
    }

    @Test
    public void testConnectReadIncomplete()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(IOException.class, TimeoutException.class);
        }
    }

    @Test
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(500, MILLISECONDS));
            config.setIdleTimeout(new Duration(500, MILLISECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(IOException.class, TimeoutException.class, IllegalStateException.class);
        }
    }

    @Test
    public void testConnectReadRequestClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, Long.MAX_VALUE, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(IOException.class, TimeoutException.class, IllegalStateException.class);
        }
    }

    @Test
    public void testConnectReadRequestWriteJunkHangup()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, "THIS\nIS\nJUNK\n\n".getBytes(), false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(Exception.class);
        }
    }

    @Test
    public void testHandlesUndeclaredThrowable()
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        assertThatThrownBy(() -> executeRequest(request, new ThrowErrorResponseHandler()))
                .isInstanceOfAny(CustomError.class);
    }

    @Test
    public void testHttpStatusListenerException()
    {
        servlet.setResponseStatusCode(TestingStatusListener.EXCEPTION_STATUS);

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        assertThatThrownBy(() -> executeRequest(request, createStatusResponseHandler()))
                .isInstanceOfAny(UncheckedIOException.class);
    }

    @Test
    public void testHttpProtocolUsed()
            throws Exception
    {
        servlet.setResponseBody("Hello world ;)");
        Request request = prepareGet()
                .setUri(baseURI)
                .build();
        HttpVersion version = executeRequest(request, new HttpVersionResponseHandler());
        if (createClientConfig().isHttp2Enabled()) {
            assertThat(version).isEqualTo(HttpVersion.HTTP_2);
        }
        else {
            assertThat(version).isEqualTo(HttpVersion.HTTP_1);
        }
    }

    @Test
    public void testDowngradedHttpProtocolUsed()
            throws Exception
    {
        servlet.setResponseBody("Hello world ;)");
        Request request = prepareGet()
                .setUri(baseURI)
                .build();
        HttpVersion version = executeRequest(upgradeRequest(request, HttpVersion.HTTP_1), new HttpVersionResponseHandler());
        assertThat(version).isEqualTo(HttpVersion.HTTP_1);
    }

    @Test
    public void testPutMethodWithLargeStreamingBodyGenerator()
            throws Exception
    {
        testPutMethodWithStreamingBodyGenerator(true);
    }

    @Test
    public void testPutMethodWithStreamingBodyGenerator()
            throws Exception
    {
        testPutMethodWithStreamingBodyGenerator(false);
    }

    protected void testPutMethodWithStreamingBodyGenerator(boolean largeContent)
            throws Exception
    {
        String content = largeContent ? LARGE_CONTENT : "hello".repeat(1000);

        URI uri = baseURI.resolve("/road/to/nowhere");
        servlet.setResponseBody(content);

        ResponseHandler<Void, RuntimeException> responseHandler = new ResponseHandler<>()
        {
            @Override
            public Void handleException(Request request, Exception exception)
                    throws RuntimeException
            {
                throw propagate(request, exception);
            }

            @Override
            public Void handle(Request request, Response response)
                    throws RuntimeException
            {
                try {
                    InputStream inputStream = response.getInputStream();
                    Request streamingRequest = preparePut()
                            .setUri(uri)
                            .addHeader(CONTENT_LENGTH, Integer.toString(content.length()))
                            .addHeader(CONTENT_TYPE, "x-test")
                            .setBodyGenerator(streamingBodyGenerator(inputStream))
                            .build();

                    ResponseHandler<Integer, RuntimeException> testResponseHandler = new ResponseHandler<>()
                    {
                        @Override
                        public Integer handleException(Request request, Exception exception)
                                throws RuntimeException
                        {
                            throw propagate(request, exception);
                        }

                        @SuppressWarnings("StatementWithEmptyBody")
                        @Override
                        public Integer handle(Request request, Response response)
                                throws RuntimeException
                        {
                            try {
                                InputStream in = response.getInputStream();
                                while (in.read() >= 0) {
                                    // do nothing
                                }
                            }
                            catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            return response.getStatusCode();
                        }
                    };

                    int statusCode = executeRequest(streamingRequest, testResponseHandler);
                    assertThat(statusCode).isEqualTo(200);
                    assertThat(servlet.getRequestMethod()).isEqualTo("PUT");
                    assertThat(servlet.getRequestUri()).isEqualTo(uri);
                    assertThat(servlet.getRequestHeaders(CONTENT_TYPE)).isEqualTo(ImmutableList.of("x-test"));
                    assertThat(servlet.getRequestBytes()).isEqualTo(content.getBytes());
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return null;
            }
        };

        try (JettyHttpClient httpClient = new JettyHttpClient("streaming-test", createClientConfig())) {
            Request request = preparePut()
                    .setUri(uri)
                    .addHeader(CONTENT_TYPE, "x-test")
                    .addHeader(CONTENT_LENGTH, Integer.toString(content.length()))
                    .setBodyGenerator(new StaticBodyGenerator(content.getBytes(UTF_8)))
                    .build();

            try (ExecutorService executorService = newVirtualThreadPerTaskExecutor()) {
                // processing the large content takes time
                executorService.submit(() -> httpClient.execute(request, responseHandler)).get(30, SECONDS);
            }
        }
    }

    private void executeExceptionRequest(HttpClientConfig config, Request request)
            throws Exception
    {
        try {
            executeRequest(config, request, new CaptureExceptionResponseHandler());
            fail("expected exception");
        }
        catch (CapturedException e) {
            throwIfInstanceOf(e.getCause(), Exception.class);
            throwIfUnchecked(e.getCause());
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
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName(host));
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
            extends Error
    {
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

    protected static class HttpVersionResponseHandler
            implements ResponseHandler<HttpVersion, RuntimeException>
    {
        @Override
        public HttpVersion handleException(Request request, Exception exception)
                throws RuntimeException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpVersion handle(Request request, Response response)
                throws RuntimeException
        {
            return response.getHttpVersion();
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
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            localSocketAddress = serverSocket.getLocalSocketAddress();

            // some systems like Linux have a large minimum backlog
            int i = 0;
            while (i <= 256) {
                if (!connect()) {
                    return;
                }
                i++;
            }
            abort(format("socket backlog is too large (%s connections accepted)", i));
        }

        @Override
        public void close()
        {
            for (Socket socket : clientSockets) {
                closeQuietly(socket);
            }
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

    protected static void closeQuietly(Closeable closeable)
    {
        try {
            if (closeable != null) {
                closeable.close();
            }
        }
        catch (IOException | RuntimeException ignored) {
        }
    }
}

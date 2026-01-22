package io.airlift.http.client;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.HttpResponseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayInputStream;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
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
import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StreamingBodyGenerator.streamingBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.Duration.nanosSince;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
public abstract class AbstractHttpClientTest
{
    private static final int DEFAULT_MIN_GZIP_SIZE = 32; // org.eclipse.jetty.compression.gzip.GzipCompression.DEFAULT_MIN_GZIP_SIZE
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public static final String LARGE_CONTENT = IntStream.range(0, 10_000_000).mapToObj(ignore -> "hello").collect(Collectors.joining());
    protected final Optional<String> keystore;

    protected AbstractHttpClientTest()
    {
        this.keystore = Optional.empty();
    }

    protected AbstractHttpClientTest(String keystore)
    {
        this.keystore = Optional.ofNullable(keystore);
    }

    protected abstract HttpClientConfig createClientConfig();

    public abstract Optional<StreamingResponse> executeStreamingRequest(CloseableTestHttpServer server, Request request)
            throws Exception;

    public abstract <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    public abstract <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    protected static Request upgradeRequest(Request request, HttpVersion version)
    {
        return fromRequest(request)
                .setVersion(version)
                .build();
    }

    @Disabled("This takes over a minute to run")
    @Test
    public void test100kGets()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere?query");
            Request request = prepareGet()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .build();

            for (int i = 0; i < 100_000; i++) {
                try {
                    int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
                    assertThat(statusCode).isEqualTo(200);
                }
                catch (Exception e) {
                    throw new Exception("Error on request " + i, e);
                }
            }
        }
    }

    @Test
    @Timeout(5)
    public void testConnectTimeout()
            throws Exception
    {
        try (BackloggedServer server = new BackloggedServer()) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, MILLISECONDS));
            config.setIdleTimeout(new Duration(2, SECONDS));

            Request request = prepareGet()
                    .setUri(new URI(getScheme(), null, "localhost", server.getPort(), "/", null, null))
                    .build();

            long start = System.nanoTime();
            try (JettyHttpClient client = new JettyHttpClient(config)) {
                client.execute(request, new CaptureExceptionResponseHandler());
                fail("expected exception");
            }
            catch (CapturedException e) {
                Throwable t = e.getCause();
                if (!(isConnectTimeout(t) || (t instanceof ClosedChannelException) || (t instanceof TimeoutException))) {
                    fail(format("unexpected exception: [%s]", getStackTraceAsString(t)));
                }
                Duration maxDuration = config.getConnectTimeout();
                maxDuration = new Duration(maxDuration.toMillis() + 300, MILLISECONDS);
                assertThat(nanosSince(start)).isLessThan(maxDuration);
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
                .setUri(new URI(getScheme(), null, "localhost", port, "/", null, null))
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
                .setUri(new URI(getScheme(), null, "localhost", port, "/", null, null))
                .build();

        Object expected = new Object();

        try (JettyHttpClient client = new JettyHttpClient("test", config)) {
            assertThat(client.execute(request, new DefaultOnExceptionResponseHandler(expected))).isEqualTo(expected);
        }
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
                .setUri(new URI(getScheme(), null, "localhost", 70_000, "/", null, null))
                .build();

        assertThatThrownBy(() -> executeExceptionRequest(config, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageMatching(".*port out of range.*");
    }

    @Test
    public void testDeleteMethod()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            Request request = prepareDelete()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("DELETE");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            assertThat(server.servlet().getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(server.servlet().getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
            assertThat(server.servlet().getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
            assertThat(server.statusCounts().count(200)).isEqualTo(1);
        }
    }

    @Test
    public void testErrorResponseBody()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseStatusCode(500);
            server.servlet().setResponseBody("body text");

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            StringResponse response = executeRequest(server, request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("body text");
        }
    }

    @Test
    public void testGetMethod()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere?query");
            Request request = prepareGet()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("GET");
            if (server.servlet().getRequestUri().toString().endsWith("=")) {
                // todo jetty client rewrites the uri string for some reason
                assertThat(server.servlet().getRequestUri()).isEqualTo(new URI(uri + "="));
            }
            else {
                assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            }
            assertThat(server.servlet().getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(server.servlet().getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
            assertThat(server.servlet().getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
            assertThat(server.statusCounts().count(200)).isEqualTo(1);
        }
    }

    @Test
    public void testResponseHeadersCaseInsensitive()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            Request request = prepareGet()
                    .setUri(uri)
                    .build();

            Response response = executeRequest(server, request, new PassThroughResponseHandler());

            assertThat(response.getHeader("date")).isNotNull();
            assertThat(response.getHeader("DATE")).isNotNull();

            assertThat(response.getHeaders("date").size()).isEqualTo(1);
            assertThat(response.getHeaders("DATE").size()).isEqualTo(1);
        }
    }

    @Test
    public void testQuotedSpace()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere?query=ab%20cd");
            Request request = prepareGet()
                    .setUri(uri)
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("GET");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
        }
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer(); JettyHttpClient client = server.createClient(createClientConfig())) {
            URI uri = URI.create(server.baseURI().toASCIIString() + "/?remotePort=");
            Request request = prepareGet()
                    .setUri(uri)
                    .build();

            StatusResponse response1 = client.execute(request, createStatusResponseHandler());
            Thread.sleep(1000);
            StatusResponse response2 = client.execute(request, createStatusResponseHandler());
            Thread.sleep(1000);
            StatusResponse response3 = client.execute(request, createStatusResponseHandler());

            assertThat(response1.getHeader("remotePort")).isNotNull();
            assertThat(response2.getHeader("remotePort")).isNotNull();
            assertThat(response3.getHeader("remotePort")).isNotNull();

            int port1 = Integer.parseInt(response1.getHeader("remotePort"));
            int port2 = Integer.parseInt(response2.getHeader("remotePort"));
            int port3 = Integer.parseInt(response3.getHeader("remotePort"));

            assertThat(port2).isEqualTo(port1);
            assertThat(port3).isEqualTo(port1);
            assertThat(port1).isBetween(1024, 65535);
        }
    }

    @Test
    public void testPostMethod()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            Request request = preparePost()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("POST");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            assertThat(server.servlet().getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(server.servlet().getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
            assertThat(server.servlet().getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
            assertThat(server.statusCounts().count(200)).isEqualTo(1);
        }
    }

    @Test
    public void testPutMethod()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            Request request = preparePut()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("PUT");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            assertThat(server.servlet().getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(server.servlet().getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
            assertThat(server.servlet().getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
            assertThat(server.statusCounts().count(200)).isEqualTo(1);
        }
    }

    @Test
    public void testPutMethodWithStaticBodyGenerator()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            byte[] body = {1, 2, 5};
            Request request = preparePut()
                    .setUri(uri)
                    .addHeader("foo", "bar")
                    .addHeader("dupe", "first")
                    .addHeader("dupe", "second")
                    .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(body))
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("PUT");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            assertThat(server.servlet().getRequestHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(server.servlet().getRequestHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
            assertThat(server.servlet().getRequestHeaders("x-custom-filter")).isEqualTo(ImmutableList.of("custom value"));
            assertThat(server.servlet().getRequestBytes()).isEqualTo(body);
            assertThat(server.statusCounts().count(200)).isEqualTo(1);
        }
    }

    @Test
    public void testPutMethodWithFileBodyGenerator()
            throws Exception
    {
        byte[] contents = "hello world".getBytes(UTF_8);
        File testFile = File.createTempFile("test", null);
        Files.write(testFile.toPath(), contents);

        try (CloseableTestHttpServer server = newServer()) {
            URI uri = server.baseURI().resolve("/road/to/nowhere");
            Request request = preparePut()
                    .setUri(uri)
                    .addHeader(CONTENT_TYPE, "x-test")
                    .setBodyGenerator(new FileBodyGenerator(testFile.toPath()))
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(200);
            assertThat(server.servlet().getRequestMethod()).isEqualTo("PUT");
            assertThat(server.servlet().getRequestUri()).isEqualTo(uri);
            assertThat(server.servlet().getRequestHeaders(CONTENT_TYPE)).isEqualTo(ImmutableList.of("x-test"));
            assertThat(server.servlet().getRequestHeaders(CONTENT_LENGTH)).isEqualTo(ImmutableList.of(String.valueOf(contents.length)));
            assertThat(server.servlet().getRequestBytes()).isEqualTo(contents);
        }

        assertThat(testFile.delete()).isTrue();
    }

    @Test
    public void testReadTimeout()
            throws Exception
    {
        HttpClientConfig config = createClientConfig()
                .setIdleTimeout(new Duration(500, MILLISECONDS));

        try (CloseableTestHttpServer server = newServer()) {
            URI uri = URI.create(server.baseURI().toASCIIString() + "/?sleep=1000");
            Request request = prepareGet()
                    .setUri(uri)
                    .build();

            assertThatThrownBy(() -> executeRequest(server, config, request, new ExceptionResponseHandler()))
                    .isInstanceOfAny(IOException.class, TimeoutException.class);
        }
    }

    @Test
    public void testReadRequestTimeout()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = URI.create(server.baseURI().toASCIIString() + "/?sleep=1000");
            Request request = prepareGet()
                    .setIdleTimeout(new Duration(500, MILLISECONDS))
                    .setUri(uri)
                    .build();

            assertThatThrownBy(() -> executeRequest(server, request, new ExceptionResponseHandler()))
                    .isInstanceOfAny(IOException.class, TimeoutException.class);
        }

        try (CloseableTestHttpServer server = newServer()) {
            URI uri = URI.create(server.baseURI().toASCIIString() + "/?sleep=1000");
            Request request = prepareGet()
                    .setRequestTimeout(new Duration(500, MILLISECONDS))
                    .setUri(uri)
                    .build();

            assertThatThrownBy(() -> executeRequest(server, request, new ExceptionResponseHandler()))
                    .isInstanceOfAny(IOException.class, TimeoutException.class);
        }
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseBody("body text");

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            StringResponse response = executeRequest(server, request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("body text");
        }
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            String body = executeRequest(server, request, createStringResponseHandler()).getBody();
            assertThat(body).isEqualTo("");
        }
    }

    @Test
    public void testResponseHeader()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().addResponseHeader("foo", "bar");
            server.servlet().addResponseHeader("dupe", "first");
            server.servlet().addResponseHeader("dupe", "second");

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            StatusResponse response = executeRequest(server, request, createStatusResponseHandler());

            assertThat(response.getHeaders("foo")).isEqualTo(ImmutableList.of("bar"));
            assertThat(response.getHeaders("dupe")).isEqualTo(ImmutableList.of("first", "second"));
        }
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseStatusCode(543);
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            int statusCode = executeRequest(server, request, createStatusResponseHandler()).getStatusCode();
            assertThat(statusCode).isEqualTo(543);
        }
    }

    @Test
    public void testExecuteStreaming()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseBody(LARGE_CONTENT);

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            executeStreamingRequest(server, request).ifPresent(streamingResponse -> {
                try (streamingResponse) {
                    String responseString = new String(streamingResponse.getInputStream().readAllBytes(), UTF_8);
                    assertThat(responseString).isEqualTo(LARGE_CONTENT);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    public void testRequestHeaders()
            throws Exception
    {
        String basic = "Basic dGVzdDphYmM=";
        String bearer = "Bearer testxyz";

        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .addHeader("X-Test", "xtest1")
                    .addHeader("X-Test", "xtest2")
                    .setHeader(USER_AGENT, "testagent")
                    .addHeader(AUTHORIZATION, basic)
                    .addHeader(AUTHORIZATION, bearer)
                    .build();

            StatusResponse response = executeRequest(server, request, createStatusResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(server.servlet().getRequestHeaders("X-Test")).containsExactly("xtest1", "xtest2");
            assertThat(server.servlet().getRequestHeaders(USER_AGENT)).containsExactly("testagent");
            assertThat(server.servlet().getRequestHeaders(AUTHORIZATION)).containsExactly(basic, bearer);
        }
    }

    @Test
    public void testFollowRedirects()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet()
                    .setUri(URI.create(server.baseURI().toASCIIString() + "/test?redirect=/redirect"))
                    .build();

            StatusResponse response = executeRequest(server, request, createStatusResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getHeader(LOCATION)).isNull();
            assertThat(server.servlet().getRequestUri()).isEqualTo(URI.create(server.baseURI().toASCIIString() + "/redirect"));

            request = Request.Builder.fromRequest(request)
                    .setFollowRedirects(false)
                    .build();

            response = executeRequest(server, request, createStatusResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(302);
            assertThat(response.getHeader(LOCATION)).isEqualTo("/redirect");
            assertThat(server.servlet().getRequestUri()).isEqualTo(request.getUri());
        }
    }

    @Test
    public void testThrowsUnexpectedResponseException()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseStatusCode(543);
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            assertThatThrownBy(() -> executeRequest(server, request, new UnexpectedResponseStatusCodeHandler(200)))
                    .isInstanceOf(UnexpectedResponseException.class);
        }
    }

    @Test
    public void testCompressionIsDisabled()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            String body = executeRequest(server, request, createStringResponseHandler()).getBody();
            assertThat(body).isEqualTo("");
            assertThat(server.servlet().getRequestHeaders().containsKey(HeaderName.of(ACCEPT_ENCODING))).isFalse();

            String json = "{\"fuite\":\"apple\",\"hello\":\"world\"}";
            assertThat(json.length()).isGreaterThanOrEqualTo(DEFAULT_MIN_GZIP_SIZE);

            server.servlet().setResponseBody(json);
            server.servlet().addResponseHeader(CONTENT_TYPE, "application/json");

            StringResponse response = executeRequest(server, request, createStringResponseHandler());
            assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/json");
            assertThat(response.getBody()).isEqualTo(json);
        }
    }

    @Test
    public void testConnectNoRead()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", 0, null, false)) {
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
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", 0, null, true)) {
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
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", 10, null, false)) {
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
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", 10, null, true)) {
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
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", Long.MAX_VALUE, null, true)) {
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
        try (FakeServer fakeServer = new FakeServer(getScheme(), "localhost", 10, "THIS\nIS\nJUNK\n\n".getBytes(), false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            assertThatThrownBy(() -> executeRequest(fakeServer, config))
                    .isInstanceOfAny(Exception.class);
        }
    }

    @Test
    public void testHandlesUndeclaredThrowable()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            assertThatThrownBy(() -> executeRequest(server, request, new ThrowErrorResponseHandler()))
                    .isInstanceOfAny(CustomError.class);
        }
    }

    @Test
    public void testHttpStatusListenerException()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseStatusCode(TestingStatusListener.EXCEPTION_STATUS);

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            assertThatThrownBy(() -> executeRequest(server, request, createStatusResponseHandler()))
                    .isInstanceOfAny(UncheckedIOException.class);
        }
    }

    @Test
    public void testHttpProtocolUsed()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseBody("Hello world ;)");
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            HttpVersion version = executeRequest(server, request, new HttpVersionResponseHandler());
            if (createClientConfig().isHttp2Enabled()) {
                assertThat(version).isEqualTo(HttpVersion.HTTP_2);
            }
            else {
                assertThat(version).isEqualTo(HttpVersion.HTTP_1);
            }
        }
    }

    @Test
    public void testDowngradedHttpProtocolUsed()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            server.servlet().setResponseBody("Hello world ;)");
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            HttpVersion version = executeRequest(server, upgradeRequest(request, HttpVersion.HTTP_1), new HttpVersionResponseHandler());
            assertThat(version).isEqualTo(HttpVersion.HTTP_1);
        }
    }

    @Test
    @Timeout(value = 5, unit = SECONDS)
    public void testPipedLargeContent()
            throws Exception
    {
        testPiped(true);
    }

    @Test
    @Timeout(value = 5, unit = SECONDS)
    public void testPipedSmallContent()
            throws Exception
    {
        testPiped(false);
    }

    protected void testPiped(boolean largeContent)
            throws Exception
    {
        String content = largeContent ? LARGE_CONTENT : "hello".repeat(1000);

        var locals = new Object()
        {
            String putContent;
            CloseableTestHttpServer server;
        };

        try (JettyHttpClient httpClient = new JettyHttpClient("streaming-test", createClientConfig())) {
            HttpServlet pipeServlet = new HttpServlet()
            {
                @Override
                protected void service(HttpServletRequest request, HttpServletResponse response)
                {
                    try {
                        switch (request.getPathInfo()) {
                            case "/get" -> {
                                response.setIntHeader("Content-Length", content.length());
                                response.setStatus(200);
                                new ByteArrayInputStream(content.getBytes(UTF_8)).transferTo(response.getOutputStream());
                            }

                            case "/pipe" -> {
                                // pipe test is here:
                                // execute a request to get content and then use the input stream as an argument
                                // to stream another put request
                                Request pipeRequest = prepareGet()
                                        .setUri(locals.server.baseURI().resolve("get"))
                                        .build();
                                try (StreamingResponse streamingResponse = httpClient.executeStreaming(pipeRequest)) {
                                    Request putRequest = preparePut()
                                            .setUri(locals.server.baseURI().resolve("put"))
                                            .setBodyGenerator(streamingBodyGenerator(streamingResponse.getInputStream()))
                                            .build();
                                    executeRequest(locals.server, putRequest, createStatusResponseHandler());
                                    response.setStatus(204);
                                }
                            }

                            case "/put" -> {
                                try (InputStream stream = request.getInputStream()) {
                                    // save the PUT content into a local for validation after the test
                                    locals.putContent = new String(stream.readAllBytes(), UTF_8);
                                    response.setStatus(204);
                                }
                            }

                            default -> response.setStatus(500);
                        }
                    }
                    catch (Exception e) {
                        fail(e);
                    }
                }
            };

            try (CloseableTestHttpServer server = new CloseableTestHttpServer(getScheme(), new TestingHttpServer(keystore, pipeServlet), HashMultiset.create(), new EchoServlet())) {
                locals.server = server;

                Request request = preparePut().setUri(locals.server.baseURI().resolve("pipe")).build();
                executeRequest(server, request, createStatusResponseHandler());

                assertThat(locals.putContent).isEqualTo(content);
            }
        }
    }

    @Test
    public void testCancelAsyncExecute()
            throws Exception
    {
        SynchronousQueue<ListenableFuture<?>> requestFutureQueue = new SynchronousQueue<>();
        HttpServlet servlet = new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws ServletException
            {
                try {
                    requestFutureQueue.take().cancel(true);
                    response.getWriter().write("everything is fine");
                }
                catch (Exception e) {
                    throw new ServletException(e);
                }
            }
        };

        try (TestingHttpServer server = new TestingHttpServer(keystore, servlet);
                JettyHttpClient client = new JettyHttpClient(createClientConfig())) {
            Request request = prepareGet()
                    .setUri(server.baseURI().resolve("/a/path"))
                    .build();
            ListenableFuture<?> future = client.executeAsync(request, createStatusResponseHandler());
            requestFutureQueue.put(future);
            assertThatThrownBy(future::get)
                    .hasMessage("Task was cancelled.");
        }
    }

    @Test
    public void testExecuteAsyncGetState()
            throws Exception
    {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch requestCanComplete = new CountDownLatch(1);
        HttpServlet servlet = new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws ServletException, IOException
            {
                try {
                    requestStarted.countDown();
                    assertThat(requestCanComplete.await(10, SECONDS)).as("requestCanComplete").isTrue();
                    response.getWriter().write("everything is fine");
                }
                catch (Exception e) {
                    throw new ServletException(e);
                }
            }
        };

        try (TestingHttpServer server = new TestingHttpServer(keystore, servlet);
                JettyHttpClient client = new JettyHttpClient(createClientConfig())) {
            Request request = prepareGet()
                    .setUri(server.baseURI().resolve("/a/path"))
                    .build();
            HttpResponseFuture<StringResponse> future = client.executeAsync(request, createStringResponseHandler());
            assertThat(requestStarted.await(10, SECONDS)).as("requestStarted").isTrue();
            assertThat(future.getState()).isEqualTo("WAITING_FOR_CONNECTION");
            requestCanComplete.countDown();
            assertThat(future.get().getBody()).isEqualTo("everything is fine");
            assertThat(future.getState()).isEqualTo("DONE");
        }
    }

    @Test
    public void testMaxContentLength()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            // Must be all ASCII.
            String text = "Hello world!".repeat(1024 / 4); // ~3KB
            server.servlet().setResponseBody(text);

            // Response within limit
            Request request = prepareGet()
                    .setUri(server.baseURI().resolve("/a/path"))
                    .setMaxResponseContentLength(DataSize.of(text.length(), BYTE))
                    .build();
            StringResponse response = executeRequest(server, request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(text);

            // Response exceeds limit
            Request limitedRequest = fromRequest(request)
                    .setMaxResponseContentLength(DataSize.of(text.length() - 1, BYTE))
                    .build();
            assertThatThrownBy(() -> executeRequest(server, limitedRequest, createStringResponseHandler()))
                    .satisfies(throwable -> {
                        if (Strings.nullToEmpty(throwable.getMessage()).startsWith("Failed reading response from server")) {
                            assertThat(throwable)
                                    .hasStackTraceContaining("InputStream exceeded maximum length of 3071");
                        }
                        else {
                            assertThat(throwable)
                                    .hasMessage("Buffering capacity 3071 exceeded");
                        }
                    });

            // Streaming execution bypasses content limits
            executeStreamingRequest(server, limitedRequest).ifPresent(streamingResponse -> {
                try (streamingResponse) {
                    assertThat(new String(streamingResponse.getInputStream().readAllBytes(), US_ASCII)).isEqualTo(text);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void executeExceptionRequest(HttpClientConfig config, Request request)
            throws Exception
    {
        try (JettyHttpClient client = new JettyHttpClient("test", config)) {
            client.execute(request, new CaptureExceptionResponseHandler());
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
            try (JettyHttpClient client = new JettyHttpClient("test", config)) {
                client.execute(request, new ExceptionResponseHandler());
            }
        }
        finally {
            assertThat(nanosSince(start)).as("Expected request to finish quickly").isLessThan(new Duration(1, SECONDS));
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

    private static class DefaultOnExceptionResponseHandler
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

    private String getScheme()
    {
        return keystore.isPresent() ? "https" : "http";
    }

    public record CloseableTestHttpServer(String scheme, TestingHttpServer server, Multiset<Integer> statusCounts, EchoServlet servlet)
            implements AutoCloseable
    {
        public CloseableTestHttpServer
        {
            requireNonNull(scheme, "scheme is null");
            requireNonNull(server, "server is null");
            requireNonNull(servlet, "servlet is null");
        }

        public URI baseURI()
        {
            return server.baseURI();
        }

        @Override
        public void close()
                throws Exception
        {
            server.close();
        }

        public JettyHttpClient createClient(HttpClientConfig config)
        {
            return new JettyHttpClient(UUID.randomUUID().toString(), config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)));
        }
    }

    protected CloseableTestHttpServer newServer()
    {
        try {
            EchoServlet servlet = new EchoServlet();
            return new CloseableTestHttpServer(getScheme(), new TestingHttpServer(keystore, servlet), HashMultiset.create(), servlet);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
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

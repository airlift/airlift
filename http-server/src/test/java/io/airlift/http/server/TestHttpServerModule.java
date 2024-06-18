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

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.AbstractEventClient;
import io.airlift.event.client.EventClient;
import io.airlift.event.client.EventModule;
import io.airlift.event.client.InMemoryEventModule;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.log.Logging;
import io.airlift.node.NodeInfo;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.tracetoken.TraceTokenModule;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.HttpHeaders.REFERER;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.util.concurrent.Futures.nonCancellationPropagating;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.http.server.HttpServerBinder.httpServerBinder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Collections.nCopies;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestHttpServerModule
{
    private File tempDir;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = createTempDirectory(null).toFile();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws IOException
    {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testCanConstructServer()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
                new TestingNodeModule(),
                new EventModule(),
                binder -> binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class));

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
    }

    @Test
    public void testHttpServerUri()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
                new TestingNodeModule(),
                new EventModule(),
                binder -> binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class));

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
        server.start();
        try {
            HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
            assertNotNull(httpServerInfo);
            assertNotNull(httpServerInfo.getHttpUri());
            assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");
            assertEquals(httpServerInfo.getHttpUri().getHost(), nodeInfo.getInternalAddress());
            assertNull(httpServerInfo.getHttpsUri());
        }
        catch (Exception e) {
            server.stop();
        }
    }

    @DataProvider
    public static Iterator<Boolean> enabledDisabled()
    {
        return ImmutableList.of(true, false).iterator();
    }

    @Test(dataProvider = "enabledDisabled")
    public void testServer(boolean enableLegacyUriCompliance)
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
                new TestingNodeModule(),
                new EventModule(),
                binder -> {
                    binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                    newSetBinder(binder, Filter.class, TheServlet.class).addBinding().to(DummyFilter.class).in(Scopes.SINGLETON);
                    httpServerBinder(binder).bindResource("/", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("/", "webapp/user2");
                    httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("path", "webapp/user2");
                    if (enableLegacyUriCompliance) {
                        httpServerBinder(binder).enableLegacyUriCompliance();
                    }
                });

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);

        HttpServer server = injector.getInstance(HttpServer.class);
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            // test servlet bound correctly
            URI httpUri = httpServerInfo.getHttpUri();
            StatusResponse response = client.execute(prepareGet().setUri(httpUri).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);

            // test filter bound correctly
            response = client.execute(prepareGet().setUri(httpUri.resolve("/filter")).build(), createStatusResponseHandler());
            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);

            if (enableLegacyUriCompliance) {
                // test legacy URI code for encoded slashes
                response = client.execute(prepareGet().setUri(httpUri.resolve("/slashtest/one/two%2fthree/four/%2f/five")).build(), createStatusResponseHandler());
                assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            }

            // test http resources
            assertResource(httpUri, client, "", "welcome user!");
            assertResource(httpUri, client, "user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "user.txt", "user");
            assertResource(httpUri, client, "user2.txt", "user2");
            assertRedirect(httpUri, client, "path", "/path/");
            assertResource(httpUri, client, "path/", "welcome user!");
            assertResource(httpUri, client, "path/user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "path/user.txt", "user");
            assertResource(httpUri, client, "path/user2.txt", "user2");
        }
        finally {
            server.stop();
        }
    }

    private void assertResource(URI baseUri, HttpClient client, String path, String contents)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponse response = client.execute(prepareGet().setUri(uriBuilder.appendPath(path).build()).build(), createStringResponseHandler());
        assertEquals(response.getStatusCode(), HttpStatus.OK.code());
        String contentType = response.getHeader(CONTENT_TYPE);
        assertNotNull(contentType, CONTENT_TYPE + " header is absent");
        MediaType mediaType = MediaType.parse(contentType);
        assertTrue(PLAIN_TEXT_UTF_8.is(mediaType), "Expected text/plain but got " + mediaType);
        assertEquals(response.getBody().trim(), contents);
    }

    private void assertRedirect(URI baseUri, HttpClient client, String path, String redirect)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponse response = client.execute(
                prepareGet()
                        .setFollowRedirects(false)
                        .setUri(uriBuilder.appendPath(path).build())
                        .build(),
                createStringResponseHandler());
        assertEquals(response.getStatusCode(), HttpStatus.TEMPORARY_REDIRECT.code());
        assertEquals(response.getHeader(LOCATION), redirect);
        assertNull(response.getHeader(CONTENT_TYPE), CONTENT_TYPE + " header should be absent");
        assertEquals(response.getBody(), "", "Response body");
    }

    @Test
    public void testHttpRequestEvent()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        SingleUseEventClient eventClient = new SingleUseEventClient();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
                new TestingNodeModule(),
                new InMemoryEventModule(),
                new TraceTokenModule(),
                binder -> newSetBinder(binder, EventClient.class).addBinding().toInstance(eventClient),
                binder -> binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(EchoServlet.class).in(Scopes.SINGLETON));

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        EchoServlet echoServlet = (EchoServlet) injector.getInstance(Key.get(Servlet.class, TheServlet.class));

        HttpServer server = injector.getInstance(HttpServer.class);
        server.start();

        URI requestUri = httpServerInfo.getHttpUri().resolve("/my/path");
        String userAgent = "my-user-agent";
        String referrer = "http://www.google.com";
        String token = "this is a trace token";
        String requestBody = Joiner.on(" ").join(nCopies(50, "request"));
        String requestContentType = "request/type";

        int responseCode = 555;
        String responseBody = Joiner.on(" ").join(nCopies(100, "response"));
        String responseContentType = "response/type";

        echoServlet.responseBody = responseBody;
        echoServlet.responseStatusCode = responseCode;
        echoServlet.responseHeaders.put("Content-Type", responseContentType);

        long beforeRequest = System.currentTimeMillis();
        long afterRequest;
        HttpRequestEvent event;
        try (JettyHttpClient client = new JettyHttpClient()) {
            // test servlet bound correctly
            StringResponse response = client.execute(
                    preparePost().setUri(requestUri)
                            .addHeader(USER_AGENT, userAgent)
                            .addHeader(CONTENT_TYPE, requestContentType)
                            .addHeader(REFERER, referrer)
                            .addHeader("X-Airlift-TraceToken", token)
                            .setBodyGenerator(createStaticBodyGenerator(requestBody, UTF_8))
                            .build(),
                    createStringResponseHandler());

            afterRequest = System.currentTimeMillis();

            assertEquals(response.getStatusCode(), responseCode);
            assertEquals(response.getBody(), responseBody);
            assertEquals(response.getHeader("Content-Type"), responseContentType);

            event = (HttpRequestEvent) eventClient.getEvent().get(10, TimeUnit.SECONDS);
        }
        finally {
            server.stop();
        }

        assertEquals(event.clientAddress(), echoServlet.remoteAddress);
        assertEquals(event.protocol(), "http");
        assertEquals(event.method(), "POST");
        assertEquals(event.requestUri(), requestUri.getPath());
        assertNull(event.user());
        assertEquals(event.agent(), userAgent);
        assertEquals(event.referrer(), referrer);
        assertEquals(event.traceToken(), token);

        assertEquals(event.requestSize(), requestBody.length());
        assertEquals(event.requestContentType(), requestContentType);
        assertEquals(event.responseSize(), responseBody.length());
        assertEquals(event.responseCode(), responseCode);
        assertEquals(event.responseContentType(), responseContentType);
        assertTrue(event.timeStamp().toEpochMilli() >= beforeRequest);
        assertTrue(event.timeToLastByte() <= afterRequest - beforeRequest);
        assertTrue(event.timeToFirstByte() <= event.timeToLastByte());
        assertTrue(event.timeToDispatch() <= event.timeToFirstByte());
        assertTrue(event.timeToFirstByte() <= event.timeToLastByte());
    }

    private static final class EchoServlet
            extends HttpServlet
    {
        private int responseStatusCode = 300;
        private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        public String responseBody;
        private String remoteAddress;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            ByteStreams.copy(request.getInputStream(), ByteStreams.nullOutputStream());

            remoteAddress = request.getRemoteAddr();
            for (Entry<String, String> entry : responseHeaders.entries()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }

            response.setStatus(responseStatusCode);

            if (responseBody != null) {
                response.getOutputStream().write(responseBody.getBytes(UTF_8));
            }
        }
    }

    private static class SingleUseEventClient
            extends AbstractEventClient
    {
        private final SettableFuture<Object> future = SettableFuture.create();

        @Override
        protected synchronized <T> void postEvent(T event)
        {
            checkState(!future.isDone(), "event already posted");
            future.set(event);
        }

        public ListenableFuture<Object> getEvent()
        {
            return nonCancellationPropagating(future);
        }
    }
}

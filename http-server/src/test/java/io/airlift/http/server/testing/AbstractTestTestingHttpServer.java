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
package io.airlift.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerFeatures;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.TheServlet;
import io.airlift.log.Logging;
import io.airlift.node.NodeInfo;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.units.Duration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.util.VirtualThreads;
import org.testng.SkipException;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.http.server.HttpServerBinder.httpServerBinder;
import static io.airlift.testing.Assertions.assertGreaterThan;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public abstract class AbstractTestTestingHttpServer
{
    private final HttpServerFeatures serverFeatures;

    AbstractTestTestingHttpServer(HttpServerFeatures serverFeatures)
    {
        this.serverFeatures = requireNonNull(serverFeatures, "serverFeatures is null");
    }

    protected HttpClientConfig getHttpClientConfig()
    {
        return new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS));
    }

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @Test
    public void testInitialization()
            throws Exception
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();
        Map<String, String> params = ImmutableMap.of("sampleInitParameter", "the value");
        TestingHttpServer server = createTestingHttpServer(serverFeatures, servlet, params);

        try {
            server.start();
            assertEquals(servlet.getSampleInitParam(), "the value");
            assertGreaterThan(server.getPort(), 0);
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testRequest()
            throws Exception
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();
        TestingHttpServer server = createTestingHttpServer(serverFeatures, servlet, ImmutableMap.of());

        try {
            server.start();

            try (HttpClient client = new JettyHttpClient(getHttpClientConfig())) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
                assertEquals(servlet.getCallCount(), 1);
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testFilteredRequest()
            throws Exception
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();
        DummyFilter filter = new DummyFilter();
        TestingHttpServer server = createTestingHttpServerWithFilter(serverFeatures, servlet, ImmutableMap.of(), filter);

        try {
            server.start();

            try (HttpClient client = new JettyHttpClient(getHttpClientConfig())) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
                assertEquals(servlet.getCallCount(), 1);
                assertEquals(filter.getCallCount(), 1);
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testGuiceInjectionWithoutFilters()
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();

        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                binder -> {
                    binder.bind(Servlet.class).annotatedWith(TheServlet.class).toInstance(servlet);
                    binder.bind(new TypeLiteral<Map<String, String>>() {}).annotatedWith(TheServlet.class).toInstance(ImmutableMap.of());
                    httpServerBinder(binder).withFeatures(serverFeatures);
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(getHttpClientConfig())) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(servlet.getCallCount(), 1);
        }
        finally {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testGuiceInjectionWithFilters()
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();
        DummyFilter filter = new DummyFilter();

        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                binder -> {
                    binder.bind(Servlet.class).annotatedWith(TheServlet.class).toInstance(servlet);
                    binder.bind(new TypeLiteral<Map<String, String>>() {}).annotatedWith(TheServlet.class).toInstance(ImmutableMap.of());
                    newSetBinder(binder, Filter.class, TheServlet.class).addBinding().toInstance(filter);
                    httpServerBinder(binder).withFeatures(serverFeatures);
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(getHttpClientConfig())) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(servlet.getCallCount(), 1);
            assertEquals(filter.getCallCount(), 1);
        }

        finally {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testGuiceInjectionWithResources()
    {
        skipUnlessJdkHasVirtualThreads();
        DummyServlet servlet = new DummyServlet();

        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(0),
                binder -> {
                    binder.bind(Servlet.class).annotatedWith(TheServlet.class).toInstance(servlet);
                    binder.bind(new TypeLiteral<Map<String, String>>() {}).annotatedWith(TheServlet.class).toInstance(ImmutableMap.of());
                    httpServerBinder(binder).bindResource("/", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("/", "webapp/user2");
                    httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("path", "webapp/user2");
                    httpServerBinder(binder).withFeatures(serverFeatures);
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(getHttpClientConfig())) {
            // test http resources
            URI uri = server.getBaseUrl();
            assertResource(uri, client, "", "welcome user!");
            assertResource(uri, client, "user-welcome.txt", "welcome user!");
            assertResource(uri, client, "user.txt", "user");
            assertResource(uri, client, "user2.txt", "user2");
            assertResource(uri, client, "path", "welcome user!");
            assertResource(uri, client, "path/", "welcome user!");
            assertResource(uri, client, "path/user-welcome.txt", "welcome user!");
            assertResource(uri, client, "path/user.txt", "user");
            assertResource(uri, client, "path/user2.txt", "user2");

            // verify that servlet did not receive resource requests
            assertEquals(servlet.getCallCount(), 0);
        }
        finally {
            lifeCycleManager.stop();
        }
    }

    private void skipUnlessJdkHasVirtualThreads()
    {
        if (serverFeatures.virtualThreads() && !VirtualThreads.areSupported()) {
            throw new SkipException("Virtual threads are not supported");
        }
    }

    private static void assertResource(URI baseUri, HttpClient client, String path, String contents)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponseHandler.StringResponse data = client.execute(prepareGet().setUri(uriBuilder.appendPath(path).build()).build(), createStringResponseHandler());
        assertEquals(data.getStatusCode(), HttpStatus.OK.code());
        MediaType contentType = MediaType.parse(data.getHeader(HttpHeaders.CONTENT_TYPE));
        assertTrue(PLAIN_TEXT_UTF_8.is(contentType), "Expected text/plain but got " + contentType);
        assertEquals(data.getBody().trim(), contents);
    }

    protected TestingHttpServer createTestingHttpServer(HttpServerFeatures serverFeatures, DummyServlet servlet, Map<String, String> params)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, servlet, params, serverFeatures);
    }

    protected TestingHttpServer createTestingHttpServerWithFilter(HttpServerFeatures serverFeatures, DummyServlet servlet, Map<String, String> params, DummyFilter filter)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, Optional.empty(), servlet, params, ImmutableSet.of(filter), ImmutableSet.of(), serverFeatures, ClientCertificate.NONE);
    }

    static class DummyServlet
            extends HttpServlet
    {
        private String sampleInitParam;
        private int callCount;

        @Override
        public synchronized void init(ServletConfig config)
        {
            sampleInitParam = config.getInitParameter("sampleInitParameter");
        }

        public synchronized String getSampleInitParam()
        {
            return sampleInitParam;
        }

        public synchronized int getCallCount()
        {
            return callCount;
        }

        @Override
        protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp)
        {
            ++callCount;
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    static class DummyFilter
            implements Filter
    {
        private final AtomicInteger callCount = new AtomicInteger();

        public int getCallCount()
        {
            return callCount.get();
        }

        @Override
        public void init(FilterConfig filterConfig) {}

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException, ServletException
        {
            callCount.incrementAndGet();
            filterChain.doFilter(servletRequest, servletResponse);
        }

        @Override
        public void destroy()
        {
        }
    }
}

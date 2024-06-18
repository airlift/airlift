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
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerConfig;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class AbstractTestTestingHttpServer
{
    private final boolean enableVirtualThreads;
    private final boolean enableLegacyUriCompliance;
    private final boolean enableCaseSensitiveHeaderCache;

    AbstractTestTestingHttpServer(boolean enableVirtualThreads, boolean enableLegacyUriCompliance, boolean enableCaseSensitiveHeaderCache)
    {
        this.enableVirtualThreads = enableVirtualThreads;
        this.enableLegacyUriCompliance = enableLegacyUriCompliance;
        this.enableCaseSensitiveHeaderCache = enableCaseSensitiveHeaderCache;
    }

    @BeforeAll
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
        TestingHttpServer server = createTestingHttpServer(enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, servlet, params);

        try {
            server.start();
            assertThat(servlet.getSampleInitParam()).isEqualTo("the value");
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
        TestingHttpServer server = createTestingHttpServer(enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, servlet, ImmutableMap.of());

        try {
            server.start();

            try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(servlet.getCallCount()).isEqualTo(1);
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
        TestingHttpServer server = createTestingHttpServerWithFilter(enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, servlet, ImmutableMap.of(), filter);

        try {
            server.start();

            try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

                assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
                assertThat(servlet.getCallCount()).isEqualTo(1);
                assertThat(filter.getCallCount()).isEqualTo(1);
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
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(servlet.getCallCount()).isEqualTo(1);
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
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
            StatusResponse response = client.execute(prepareGet().setUri(server.getBaseUrl()).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(servlet.getCallCount()).isEqualTo(1);
            assertThat(filter.getCallCount()).isEqualTo(1);
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
                    if (enableVirtualThreads) {
                        httpServerBinder(binder).enableVirtualThreads();
                    }
                });

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);

        try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
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
            assertThat(servlet.getCallCount()).isEqualTo(0);
        }
        finally {
            lifeCycleManager.stop();
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Test
    public void testHeaderCaseSensitivity()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        TestingHttpServer server = createTestingHttpServer(enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, servlet, ImmutableMap.of());

        try {
            server.start();

            String contentType = "text/plain; charset=UTF-8";
            String finalContentType = "text/plain; charset=utf-8";

            try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                // run a few times to prime the Jetty cache
                for (int i = 0; i < 3; ++i) {
                    Request request = prepareGet()
                            .setUri(server.getBaseUrl())
                            .setHeader(HttpHeaders.CONTENT_TYPE, (i > 1) ? finalContentType : contentType)
                            .build();
                    client.execute(request, createStatusResponseHandler());
                }
            }

            String contentTypeHeader;
            synchronized (servlet) {
                contentTypeHeader = servlet.contentTypeHeader;
            }
            if (enableCaseSensitiveHeaderCache) {
                assertThat(contentTypeHeader).isEqualTo(finalContentType);
            }
            else {
                assertThat(contentTypeHeader).isEqualTo(contentType);
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testForwardedHeaderIsRejected()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        TestingHttpServer server = createTestingHttpServer(enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, servlet, ImmutableMap.of());

        try {
            server.start();
            try (HttpClient client = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .setHeader(HttpHeaders.X_FORWARDED_FOR, "129.0.0.1")
                        .setHeader(HttpHeaders.X_FORWARDED_HOST, "localhost.localdomain")
                        .build();
                StringResponseHandler.StringResponse execute = client.execute(request, createStringResponseHandler());
                assertThat(execute.getStatusCode()).isEqualTo(406);
                assertThat(execute.getBody())
                        .containsAnyOf("Server configuration does not allow processing of the X-Forwarded-For", "Server configuration does not allow processing of the X-Forwarded-Host");
            }
        }
        finally {
            server.stop();
        }
    }

    private void skipUnlessJdkHasVirtualThreads()
    {
        if (enableVirtualThreads && !VirtualThreads.areSupported()) {
            abort("Virtual threads are not supported");
        }
    }

    private static void assertResource(URI baseUri, HttpClient client, String path, String contents)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponseHandler.StringResponse data = client.execute(prepareGet().setUri(uriBuilder.appendPath(path).build()).build(), createStringResponseHandler());
        assertThat(data.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        MediaType contentType = MediaType.parse(data.getHeader(HttpHeaders.CONTENT_TYPE));
        assertThat(PLAIN_TEXT_UTF_8.is(contentType)).as("Expected text/plain but got " + contentType).isTrue();
        assertThat(data.getBody().trim()).isEqualTo(contents);
    }

    private static TestingHttpServer createTestingHttpServer(boolean enableVirtualThreads, boolean enableLegacyUriCompliance, boolean enableCaseSensitiveHeaderCache, DummyServlet servlet, Map<String, String> params)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, servlet, params, enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache);
    }

    private static TestingHttpServer createTestingHttpServerWithFilter(boolean enableVirtualThreads, boolean enableLegacyUriCompliance, boolean enableCaseSensitiveHeaderCache, DummyServlet servlet, Map<String, String> params, DummyFilter filter)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, Optional.empty(), servlet, params, ImmutableSet.of(filter), ImmutableSet.of(), enableVirtualThreads, enableLegacyUriCompliance, enableCaseSensitiveHeaderCache, ClientCertificate.NONE);
    }

    static class DummyServlet
            extends HttpServlet
    {
        private String sampleInitParam;
        private int callCount;
        private String contentTypeHeader;

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
            contentTypeHeader = req.getHeader(HttpHeaders.CONTENT_TYPE);
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

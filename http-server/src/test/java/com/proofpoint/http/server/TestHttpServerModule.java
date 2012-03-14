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
package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.event.client.EventClient;
import com.proofpoint.event.client.InMemoryEventClient;
import com.proofpoint.event.client.InMemoryEventModule;
import com.proofpoint.event.client.NullEventModule;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.NodeModule;
import com.proofpoint.testing.FileUtils;
import com.proofpoint.tracetoken.TraceTokenModule;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Collections.nCopies;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestHttpServerModule
{
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testCanConstructServer()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new HttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new NullEventModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                    }
                });

        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
    }

    @Test
    public void testHttpServerUri()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new HttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new NullEventModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                    }
                });

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
        server.start();
        try {
            HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
            assertNotNull(httpServerInfo);
            assertNotNull(httpServerInfo.getHttpUri());
            assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");
            assertEquals(httpServerInfo.getHttpUri().getHost(), nodeInfo.getInternalIp().getHostAddress());
            assertNull(httpServerInfo.getHttpsUri());
        }
        catch (Exception e) {
            server.stop();
        }
    }

    @Test
    public void testServer()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new HttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new NullEventModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                        Multibinder.newSetBinder(binder, Filter.class, TheServlet.class).addBinding().to(DummyFilter.class).in(Scopes.SINGLETON);
                    }
                });

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);

        HttpServer server = injector.getInstance(HttpServer.class);
        server.start();

        try {
            AsyncHttpClient client = new AsyncHttpClient();

            // test servlet bound correctly
            Response response = client.prepareGet(httpServerInfo.getHttpUri().toString())
                    .execute()
                    .get();

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);

            // test filter bound correctly
            response = client.prepareGet(httpServerInfo.getHttpUri().resolve("/filter").toString())
                    .execute()
                    .get();

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);
            assertEquals(response.getStatusText(), "filtered");
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void testHttpRequestEvent()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new HttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new InMemoryEventModule(),
                new TraceTokenModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(EchoServlet.class).in(Scopes.SINGLETON);
                    }
                });

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        InMemoryEventClient eventClient = (InMemoryEventClient) injector.getInstance(EventClient.class);
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
        try {
            AsyncHttpClient client = new AsyncHttpClient();

            // test servlet bound correctly
            Response response = client.preparePost(requestUri.toASCIIString())
                    .setHeader("User-Agent", userAgent)
                    .setHeader("Content-Type", requestContentType)
                    .setHeader("Referer", referrer)
                    .setHeader("X-Proofpoint-TraceToken", token)
                    .setBody(requestBody)
                    .execute()
                    .get();
            afterRequest = System.currentTimeMillis();

            assertEquals(response.getStatusCode(), responseCode);
            assertEquals(response.getResponseBody(), responseBody);
            assertEquals(response.getHeader("Content-Type"), responseContentType);
        }
        finally {
            server.stop();
        }

        List<Object> events = eventClient.getEvents();
        Assert.assertEquals(events.size(), 1);
        HttpRequestEvent event = (HttpRequestEvent) events.get(0);


        Assert.assertEquals(event.getClientAddress(), echoServlet.remoteAddress);
        Assert.assertEquals(event.getProtocol(), "http");
        Assert.assertEquals(event.getMethod(), "POST");
        Assert.assertEquals(event.getRequestUri(), requestUri.getPath());
        Assert.assertNull(event.getUser());
        Assert.assertEquals(event.getAgent(), userAgent);
        Assert.assertEquals(event.getReferrer(), referrer);
        Assert.assertEquals(event.getTraceToken(), token);

        Assert.assertEquals(event.getRequestSize(), requestBody.length());
        Assert.assertEquals(event.getRequestContentType(), requestContentType);

        Assert.assertEquals(event.getResponseSize(), responseBody.length());
        Assert.assertEquals(event.getResponseCode(), responseCode);
        Assert.assertEquals(event.getResponseContentType(), responseContentType);

        Assert.assertTrue(event.getTimeStamp().getMillis() >= beforeRequest);
        Assert.assertTrue(event.getTimeToLastByte() <= afterRequest - beforeRequest);
        Assert.assertNotNull(event.getTimeToFirstByte());
        Assert.assertTrue(event.getTimeToDispatch() <= event.getTimeToFirstByte());
        Assert.assertTrue(event.getTimeToFirstByte() <= event.getTimeToLastByte());
    }

    private static final class EchoServlet extends HttpServlet
    {
        private int responseStatusCode = 300;
        private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        public String responseBody;
        private String remoteAddress;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            remoteAddress = request.getRemoteAddr();
            for (Entry<String, String> entry : responseHeaders.entries()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }

            response.setStatus(responseStatusCode);

            if (responseBody != null) {
                response.getOutputStream().write(responseBody.getBytes(Charsets.UTF_8));
            }
        }
    }
}

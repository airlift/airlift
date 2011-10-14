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
package com.proofpoint.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.NodeModule;
import org.testng.annotations.Test;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.Assertions.assertGreaterThan;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

public class TestTestingHttpServer
{
    @Test
    public void testInitialization()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", "0")
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new ServletModule()
                {
                    @Override
                    public void configureServlets()
                    {
                        bind(DummyServlet.class).in(Scopes.SINGLETON);
                        serve("/*").with(DummyServlet.class, ImmutableMap.of("sampleInitParameter", "the value"));
                    }
                });

        TestingHttpServer server = injector.getInstance(TestingHttpServer.class);
        DummyServlet servlet = injector.getInstance(DummyServlet.class);

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
        TestingHttpServer server = null;
        AsyncHttpClient client = null;

        try {
            Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                    .put("node.environment", "test")
                    .put("http-server.http.port", "0")
                    .build();

            ConfigurationFactory configFactory = new ConfigurationFactory(properties);
            Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                    new NodeModule(),
                    new ConfigurationModule(configFactory),
                    new ServletModule()
                    {
                        @Override
                        public void configureServlets()
                        {
                            bind(DummyServlet.class).in(Scopes.SINGLETON);
                            serve("/*").with(DummyServlet.class, ImmutableMap.of("sampleInitParameter", "the value"));
                        }
                    });

            server = injector.getInstance(TestingHttpServer.class);
            DummyServlet servlet = injector.getInstance(DummyServlet.class);

            client = new AsyncHttpClient();

            server.start();
            assertGreaterThan(server.getPort(), 0);

            Response response = client.prepareGet(format("http://localhost:%d/", server.getPort()))
                    .execute()
                    .get(1, TimeUnit.SECONDS);

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(servlet.getCallCount(), 1);
        }
        finally {
            if (server != null) {
                closeQuietly(server);
            }
            if (client != null) {
                closeQuietly(client);
            }
        }
    }

    private TestingHttpServer createTestingHttpServer()
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config);
    }

    private void closeQuietly(TestingHttpServer server)
    {
        try {
            server.stop();
        }
        catch (Throwable e) {
            // ignore
        }
    }

    private void closeQuietly(AsyncHttpClient client)
    {
        try {
            client.close();
        }
        catch (Throwable e) {
            // ignore
        }
    }

    static class DummyServlet
            extends HttpServlet
    {
        private String sampleInitParam;
        private int callCount;

        @Override
        public synchronized void init(ServletConfig config)
                throws ServletException
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
                throws ServletException, IOException
        {
            ++callCount;
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

}

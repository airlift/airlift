/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.http.client.jetty;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.proofpoint.http.client.InputStreamBodySource;
import com.proofpoint.http.client.ResponseStatusCodeHandler;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.TestingRequestFilter;
import com.proofpoint.log.Logging;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestStressJetty
{
    private Server server;
    private URI baseURI;
    private String scheme = "http";
    private String host = "127.0.0.1";
    private static final int NUM_REQUESTS = 10_000;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();

        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        baseURI = new URI(scheme, null, host, port, null, null, null);

        Server server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        ServerConnector connector;
        connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));

        connector.setIdleTimeout(30000);
        connector.setName(scheme);
        connector.setPort(port);

        server.addConnector(connector);

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

    @Test(enabled = false, description = "This takes about 30 seconds to run")
    public void testSimultaneousGets()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        assertSimultaneousRequests(request);
    }

    @Test(enabled = false, description = "This takes about 30 seconds to run")
    public void testSimultaneousPutsInputStreamBodySource()
            throws Exception
    {
        final URI uri = baseURI.resolve("/road/to/nowhere?query");
        Supplier<Request> requestSupplier = new Supplier<Request>()
        {
            @Override
            public Request get()
            {
                return preparePut()
                        .setUri(uri)
                        .setBodySource(new InputStreamBodySource(new InputStream()
                        {
                            AtomicInteger invocation = new AtomicInteger(0);

                            @Override
                            public int read()
                            {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public int read(byte[] b)
                                    throws IOException
                            {
                                if (invocation.getAndIncrement() < 100) {
                                    Arrays.fill(b, 0, 1000, (byte) 0);
                                    return 1000;
                                }
                                else {
                                    return -1;
                                }
                            }
                        }))
                        .build();
            }
        };

        assertSimultaneousRequests(requestSupplier);
    }

    @Test(enabled = false, description = "This takes about 30 seconds to run")
    public void testSimultaneousPutsDynamicBodySource()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = preparePut()
                .setUri(uri)
                .setBodySource(new DynamicBodySource()
                {
                    @Override
                    public Writer start(final OutputStream out)
                            throws Exception
                    {
                        return new Writer()
                        {
                            AtomicInteger invocation = new AtomicInteger(0);

                            @Override
                            public void write()
                                    throws IOException
                            {
                                if (invocation.getAndIncrement() < 100) {
                                    out.write(new byte[1000]);
                                }
                                else {
                                    out.close();
                                }
                            }
                        };
                    }
                })
                .build();

        assertSimultaneousRequests(request);
    }

    @Test(enabled = false, description = "Deadlocks JettyHttpClient")
    @SuppressWarnings("deprecation")
    public void testSimultaneousPutsBodyGenerator()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = preparePut()
                .setUri(uri)
                .setBodySource(new BodyGenerator()
                {
                    @Override
                    public void write(OutputStream out)
                            throws Exception
                    {
                        for (int i = 0; i < 100; ++i) {
                            out.write(new byte[1000]);
                        }
                    }
                })
                .build();

        assertSimultaneousRequests(request);
    }

    private static void assertSimultaneousRequests(Request request)
            throws InterruptedException
    {
        assertSimultaneousRequests(Suppliers.ofInstance(request));

    }

    private static void assertSimultaneousRequests(Supplier<Request> requestSupplier)
            throws InterruptedException
    {
        final CountDownLatch completionLatch = new CountDownLatch(NUM_REQUESTS);
        try (
                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig().setMaxThreads(10));
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig().setMaxRequestsQueuedPerDestination(NUM_REQUESTS), jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
        ) {
            for (int i = 0; i < NUM_REQUESTS; i++) {
                HttpResponseFuture<Integer> future = client.executeAsync(requestSupplier.get(), new ResponseStatusCodeHandler());
                final int requestNumber = i;
                Futures.addCallback(future, new FutureCallback<Integer>()
                {
                    @Override
                    public void onSuccess(Integer statusCode)
                    {
                        try {
                            assertEquals((int) statusCode, 200, "Status code on request " + requestNumber);
                        }
                        finally {
                            completionLatch.countDown();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t)
                    {
                        try {
                            fail("Error on request " + requestNumber, t);
                        }
                        finally {
                            completionLatch.countDown();
                        }
                    }
                });
            }
            completionLatch.await();
        }
    }

    private static class DummyServlet
            extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException
        {
            resp.setStatus(HttpServletResponse.SC_OK);
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException
        {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
}

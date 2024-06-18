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
package io.airlift.event.client.http;

import com.google.common.io.CharStreams;
import io.airlift.discovery.client.HttpServiceSelector;
import io.airlift.discovery.client.testing.StaticHttpServiceSelector;
import io.airlift.event.client.EventTypeMetadata;
import io.airlift.event.client.FixedDummyEventClass;
import io.airlift.event.client.JsonEventWriter;
import io.airlift.event.client.ServiceUnavailableException;
import io.airlift.event.client.TestingUtils;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static io.airlift.event.client.TestingUtils.getNormalizedJson;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Test(singleThreaded = true)
public class TestHttpEventClient
{
    private DummyServlet servlet;
    private HttpEventClient client;
    private Server server;
    private URI baseUri;
    private HttpClient httpClient;

    @Test(expectedExceptions = ServiceUnavailableException.class, expectedExceptionsMessageRegExp = ".*is not available.*")
    public void testFutureFailsWhenServiceUnavailable()
            throws ExecutionException, InterruptedException
    {
        client = newEventClient(Collections.<URI>emptyList());

        try {
            client.post(new FixedDummyEventClass("host", Instant.now(), UUID.randomUUID(), 1, "foo")).get();
        }
        catch (ExecutionException e) {
            throwIfUnchecked(e.getCause());
            throw new RuntimeException(e.getCause());
        }
    }

    @Test
    public void testCallSucceedsWhenServiceUnavailable()
            throws ExecutionException, InterruptedException
    {
        client = newEventClient(Collections.<URI>emptyList());

        client.post(new FixedDummyEventClass("host", Instant.now(), UUID.randomUUID(), 1, "foo"));

        assertThat(servlet.lastPath).isNull();
        assertThat(servlet.lastBody).isNull();
    }

    @Test
    public void testReceivesEvent()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(asList(baseUri));

        client.post(TestingUtils.getEvents()).get();

        assertThat(servlet.lastPath).isEqualTo("/v2/event");
        assertThat(servlet.lastBody).isEqualTo(getNormalizedJson("events.json"));
    }

    @Test
    public void loadTest()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(asList(baseUri));

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(client.post(TestingUtils.getEvents()));
        }

        for (Future<Void> future : futures) {
            future.get();
        }
        assertThat(servlet.lastPath).isEqualTo("/v2/event");
        assertThat(servlet.lastBody).isEqualTo(getNormalizedJson("events.json"));
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        httpClient = new JettyHttpClient(new HttpClientConfig().setConnectTimeout(new Duration(10, SECONDS)));

        servlet = new DummyServlet();
        server = createServer(servlet);
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        httpClient.close();
    }

    private HttpEventClient newEventClient(List<URI> uris)
    {
        HttpServiceSelector selector = new StaticHttpServiceSelector("collector", "general", uris);

        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(FixedDummyEventClass.class);
        JsonEventWriter eventWriter = new JsonEventWriter(eventTypes);

        return new HttpEventClient(
                selector,
                eventWriter,
                new NodeInfo("test"),
                httpClient);
    }

    private Server createServer(final DummyServlet servlet)
            throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        baseUri = new URI("http", null, "127.0.0.1", port, null, null, null);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        server = new Server();

        ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        httpConnector.setPort(port);
        httpConnector.setName("http");
        server.addConnector(httpConnector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);
        return server;
    }

    private static class DummyServlet
            extends HttpServlet
    {
        private volatile String lastPath;
        private volatile String lastBody;

        private DummyServlet()
        {
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            lastPath = request.getPathInfo();
            lastBody = CharStreams.toString(new InputStreamReader(request.getInputStream(), UTF_8));
        }
    }
}

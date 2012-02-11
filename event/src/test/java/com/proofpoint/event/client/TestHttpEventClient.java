package com.proofpoint.event.client;

import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.testing.StaticHttpServiceSelector;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.testing.TestingNodeModule;
import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static com.proofpoint.event.client.TestingUtils.getNormalizedJson;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestHttpEventClient
{
    private DummyServlet servlet;
    private HttpEventClient client;
    private TestingHttpServer server;

    @Test(expectedExceptions = ServiceUnavailableException.class, expectedExceptionsMessageRegExp = ".*is not available.*")
    public void testFutureFailsWhenServiceUnavailable()
            throws ExecutionException, InterruptedException
    {
        client = newEventClient(Collections.<URI>emptyList());

        try {
            client.post(new FixedDummyEventClass("host", new DateTime(), UUID.randomUUID(), 1, "foo")).get();
        }
        catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

    @Test
    public void testCallSucceedsWhenServiceUnavailable()
            throws ExecutionException, InterruptedException
    {
        client = newEventClient(Collections.<URI>emptyList());

        client.post(new FixedDummyEventClass("host", new DateTime(), UUID.randomUUID(), 1, "foo"));

        assertNull(servlet.lastPath);
        assertNull(servlet.lastBody);
    }

    @Test
    public void testReceivesEvent()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(asList(server.getBaseUrl()));

        client.post(TestingUtils.getEvents()).get();

        assertEquals(servlet.lastPath, "/v2/event");
        assertEquals(servlet.lastBody, getNormalizedJson("events.json"));
    }

    @Test
    public void loadTest()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(asList(server.getBaseUrl()));

        List<Future<Void>> futures = newArrayList();
        for (int i = 0; i < 100; i++) {
            futures.add(client.post(TestingUtils.getEvents()));
        }

        for (Future<Void> future : futures) {
            future.get();
            System.out.println("future " + future);
        }
        assertEquals(servlet.lastPath, "/v2/event");
        assertEquals(servlet.lastBody, getNormalizedJson("events.json"));
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        servlet = new DummyServlet();
        server = createServer(servlet);
        server.start();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    private HttpEventClient newEventClient(List<URI> v2Uris)
    {
        HttpServiceSelector v1Selector = new StaticHttpServiceSelector("event", "general", Collections.<URI>emptyList());
        HttpServiceSelector v2Selector = new StaticHttpServiceSelector("collector", "general", v2Uris);

        HttpEventClientConfig config = new HttpEventClientConfig();

        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(FixedDummyEventClass.class);
        JsonEventWriter eventWriter = new JsonEventWriter(eventTypes, config);

        return new HttpEventClient(v1Selector, v2Selector, eventWriter, new NodeInfo("test"), config, new HttpClient(Executors.newCachedThreadPool()));
    }

    private TestingHttpServer createServer(final DummyServlet servlet)
    {
        return Guice.createInjector(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Servlet.class)
                                .annotatedWith(TheServlet.class)
                                .toInstance(servlet);

                        binder.bind(new TypeLiteral<Map<String, String>>()
                        {
                        })
                                .annotatedWith(TheServlet.class)
                                .toInstance(Collections.<String, String>emptyMap());
                    }
                })
                .getInstance(TestingHttpServer.class);
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
            lastBody = CharStreams.toString(new InputStreamReader(request.getInputStream(), "UTF-8"));
        }
    }
}

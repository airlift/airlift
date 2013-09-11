package io.airlift.http.client;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.AbstractHttpClientTest.ResponseStatusCodeHandler;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestHttpClientModule
{
    @Inject
    @ForTest
    private HttpClient httpClient;

    @Inject
    private LifeCycleManager manager;

    protected EchoServlet servlet;
    protected Server server;
    protected URI baseURI;
    private String scheme = "http";
    private String host = "127.0.0.1";

    @BeforeMethod
    public void setUp()
        throws Exception
    {
        servlet = new EchoServlet();

        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        baseURI = new URI(scheme, null, host, port, null, null, null);

        Server server = new Server();
        server.setSendServerVersion(false);

        SelectChannelConnector httpConnector = new SelectChannelConnector();

        httpConnector.setName(scheme);
        httpConnector.setPort(port);
        server.addConnector(httpConnector);

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
    public void tearDown()
        throws Exception
    {
        assertNotNull(server);
        assertNotNull(manager);

        server.stop();
        manager.stop();
    }

    @Test
    public void TestSyncHttpClient()
        throws Exception
    {
        doTest(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                HttpClientBinder.httpClientBinder(binder).bindHttpClient("test", ForTest.class);
            }
        });
    }

    @Test
    public void TestAsyncHttpClient()
        throws Exception
    {
        doTest(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                HttpClientBinder.httpClientBinder(binder).bindAsyncHttpClient("test", ForTest.class);
            }
        });
    }

    private void doTest(Module module)
        throws Exception
    {
        Injector injector = new Bootstrap(module)
            .doNotInitializeLogging()
            .strictConfig()
            .initialize();

        injector.injectMembers(this);

        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
            .setUri(uri)
            .addHeader("foo", "bar")
            .addHeader("dupe", "first")
            .addHeader("dupe", "second")
            .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler());
        assertEquals(statusCode, 200);
    }
}

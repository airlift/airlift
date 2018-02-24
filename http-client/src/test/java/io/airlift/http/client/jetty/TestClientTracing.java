package io.airlift.http.client.jetty;

import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.tracetoken.TraceTokenModule;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Qualifier;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.eclipse.jetty.http.HttpVersion.HTTP_1_1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(singleThreaded = true)
public class TestClientTracing
{
    private Server server;
    private Path tmpLogPath;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        tmpLogPath = Files.createTempDirectory("test");
        server = new Server();
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        ServerConnector connector;
        connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        connector.setIdleTimeout(60_000);
        connector.setName("http");
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(TestServlet.class, "/test");

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[] {context, new DefaultHandler()});
        server.setHandler(handlers);
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
        Files.walk(tmpLogPath)
                .map(Path::toFile)
                .forEach(File::delete);
        Files.delete(tmpLogPath);
    }

    @Test
    public void testClientTracing()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", TestClient.class)
                        .withTracing()
                        .withConfigDefaults(config -> {
                            config.setLogEnabled(true);
                            config.setLogPath(tmpLogPath.toAbsolutePath().toString());
                        }),
                new TraceTokenModule())
                .quiet()
                .strictConfig()
                .initialize();
        JettyHttpClient httpClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, TestClient.class));
        int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        URI uri = new URI("http", null, "127.0.0.1", port, null, null, null);
        StringResponse response = httpClient.execute(Request.Builder.prepareGet().setUri(uri.resolve("/test")).build(), createStringResponseHandler());
        assertEquals(response.getStatusCode(), SC_OK);

        // flush the logs
        httpClient.close();

        String logFilePath = Paths.get(tmpLogPath.toAbsolutePath().toString(), "foo-http-client.log").toAbsolutePath().toString();
        String actual = com.google.common.io.Files.toString(new File(logFilePath), UTF_8);
        String[] columns = actual.trim().split("\\t");
        assertEquals(columns[1], HTTP_1_1.toString());
        assertEquals(columns[2], "GET");
        assertEquals(columns[3], uri.resolve("/test").toString());
        assertEquals(columns[4], Integer.toString(SC_OK));
        assertNotNull(columns[7]);
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface TestClient
    {
    }
}

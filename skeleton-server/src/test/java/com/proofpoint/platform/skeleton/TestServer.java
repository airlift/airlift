package com.proofpoint.platform.skeleton;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.jmx.JmxHttpModule;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.jmx.JmxModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static javax.ws.rs.core.Response.Status.OK;
import static org.testng.Assert.assertEquals;

public class TestServer
{
    private HttpClient client;
    private TestingHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        // TODO: wrap all this stuff in a TestBootstrap class
        Injector injector = Guice.createInjector(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new JmxHttpModule(),
                new JmxModule(),
                new MainModule(),
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        server = injector.getInstance(TestingHttpServer.class);

        server.start();
        client = new ApacheHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testNothing()
            throws Exception
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(uriFor("/v1/jmx/mbean")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), OK.getStatusCode());
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}

package com.proofpoint.platform.skeleton;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jmx.JmxHttpModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.testng.Assert.assertEquals;

public class TestServer
{
    private HttpClient client;
    private TestingHttpServer server;
    private LifeCycleManager lifeCycleManager;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new JmxHttpModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new MainModule()
                );

        Injector injector = app
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new JettyHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (lifeCycleManager != null) {
                lifeCycleManager.stop();
            }
        }
        finally {
            Closeables.closeQuietly(client);
        }
    }

    @Test
    public void testNothing()
            throws Exception
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(uriFor("/nothing")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NOT_FOUND.getStatusCode());
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}

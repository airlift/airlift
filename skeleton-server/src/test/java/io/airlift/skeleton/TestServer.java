package io.airlift.skeleton;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jmx.JmxHttpModule;
import io.airlift.jmx.JmxModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestServer
{
    private HttpClient client;
    private TestingHttpServer server;
    private LifeCycleManager lifeCycleManager;

    @BeforeEach
    public void setup()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new JmxHttpModule(),
                new JmxModule(),
                new MainModule());

        Injector injector = app
                .doNotInitializeLogging()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new JettyHttpClient();
    }

    @AfterEach
    public void teardown()
    {
        try (HttpClient ignored = client) {
            if (lifeCycleManager != null) {
                lifeCycleManager.stop();
            }
        }
    }

    @Test
    public void testNothing()
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(uriFor("/v1/jmx/mbean")).build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}

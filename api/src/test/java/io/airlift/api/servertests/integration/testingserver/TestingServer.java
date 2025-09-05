package io.airlift.api.servertests.integration.testingserver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.node.NodeModule;
import jakarta.ws.rs.core.UriBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

public class TestingServer
        implements Closeable
{
    private static final Logger log = Logger.get(TestingServer.class);

    private final Injector injector;
    private final URI baseUri;

    public TestingServer(int port)
    {
        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(new TestingServerModule())
                .add(new NodeModule())
                .add(new TestingHttpServerModule(port))
                .add(new JsonModule())
                .add(new JaxrsModule());

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        baseUri = UriBuilder.fromUri(httpServerInfo.getHttpsUri() != null ? httpServerInfo.getHttpsUri() : httpServerInfo.getHttpUri())
                .host("localhost")
                .build();
    }

    public static void main(String[] args)
    {
        new TestingServer(8080);

        log.info("======== SERVER STARTED ========");
    }

    @Override
    public void close()
            throws IOException
    {
        injector.getInstance(LifeCycleManager.class).stop();
    }

    public URI baseUri()
    {
        return baseUri;
    }

    public Injector injector()
    {
        return injector;
    }
}

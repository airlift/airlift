package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClient;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;

public class TestingServer
        implements Closeable
{
    private final Injector injector;

    public TestingServer(Optional<Module> additionalModule, Function<McpModule.Builder, Module> mcpModuleApplicator)
    {
        McpModule.Builder mcpModuleBuilder = McpModule.builder()
                .withAllInClass(TestingEndpoints.class);

        Module mcpModule = mcpModuleApplicator.apply(mcpModuleBuilder);

        ImmutableList.Builder<com.google.inject.Module> modules = ImmutableList.<Module>builder()
                .add(mcpModule)
                .add(binder -> httpClientBinder(binder).bindHttpClient("test", ForTest.class))
                .add(new NodeModule())
                .add(new TestingHttpServerModule("testing"))
                .add(new JaxrsModule())
                .add(new JsonModule());

        additionalModule.ifPresent(modules::add);

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();
    }

    public Injector injector()
    {
        return injector;
    }

    public HttpClient httpClient()
    {
        return injector.getInstance(Key.get(HttpClient.class, ForTest.class));
    }

    @Override
    public void close()
    {
        injector.getInstance(LifeCycleManager.class).stop();
    }
}

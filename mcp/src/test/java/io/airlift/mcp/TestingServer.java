package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;

import java.io.Closeable;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;

public class TestingServer
        implements Closeable
{
    private final Injector injector;

    public TestingServer(McpIdentityMapper identityMapper, Module module)
    {
        com.google.inject.Module mcpModule = McpModule.builder()
                .withAllInClass(TestingEndpoints.class)
                .withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance(identityMapper))
                .build();

        ImmutableList.Builder<Module> modules = ImmutableList.<com.google.inject.Module>builder()
                .add(mcpModule)
                .add(module)
                .add(binder -> httpClientBinder(binder).bindHttpClient("test", ForTest.class))
                .add(new NodeModule())
                .add(new TestingHttpServerModule())
                .add(new JaxrsModule())
                .add(new JsonModule());

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();
    }

    public Injector injector()
    {
        return injector;
    }

    @Override
    public void close()
    {
        injector.getInstance(LifeCycleManager.class).stop();
    }
}

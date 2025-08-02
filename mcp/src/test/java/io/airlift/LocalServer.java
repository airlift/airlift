package io.airlift;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.mcp.McpModule;
import io.airlift.mcp.TestingEndpoints;
import io.airlift.mcp.TestingSessionController;
import io.airlift.mcp.model.PaginationMetadata;
import io.airlift.mcp.session.SessionMetadata;
import io.airlift.node.NodeModule;

import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;

public class LocalServer
{
    private LocalServer() {}

    private static final Logger log = Logger.get(LocalServer.class);

    public static void main(String[] args)
    {
        Optional<Integer> port = switch (args.length) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(Integer.parseInt(args[0]));
            default -> {
                System.err.println("Usage: LocalServer [port]");
                yield Optional.empty();
            }
        };

        McpModule.Builder builder = McpModule.builder()
                .addAllInClass(TestingEndpoints.class)
                .withSessionHandling(SessionMetadata.DEFAULT, binding -> binding.to(TestingSessionController.class).in(SINGLETON))
                .withPaginationMetadata(new PaginationMetadata(7)); // so that we can test pagination
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(TestingSessionController.class).in(SINGLETON);
            }

            @SuppressWarnings("unused")
            @Provides
            @Singleton
            public Optional<TestingSessionController> sessionController(TestingSessionController sessionController)
            {
                return Optional.of(sessionController);
            }
        };
        start(builder, port, module);
    }

    public static void start(McpModule.Builder builder, Optional<Integer> port, Module additionalModule)
    {
        ImmutableList.Builder<com.google.inject.Module> modules = ImmutableList.<Module>builder()
                .add(builder.build())
                .add(additionalModule)
                .add(new NodeModule())
                .add(new TestingHttpServerModule(port.orElse(0)))
                .add(new JsonModule())
                .add(new JaxrsModule())
                .add(binder -> httpClientBinder(binder).bindHttpClient("test", ForTesting.class));

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        Injector injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();

        log.info("Local server started at: %s/mcp", injector.getInstance(HttpServerInfo.class).getHttpUri());
    }
}

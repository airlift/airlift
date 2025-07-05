package io.airlift;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.mcp.McpModule;
import io.airlift.mcp.TestingEndpoints;
import io.airlift.node.NodeModule;

import java.util.Optional;

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
        start(McpModule.builder().addAllInClass(TestingEndpoints.class), port);
    }

    public static void start(McpModule.Builder builder, Optional<Integer> port)
    {
        ImmutableList.Builder<com.google.inject.Module> modules = ImmutableList.<Module>builder()
                .add(builder.build())
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

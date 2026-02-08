package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.node.NodeModule;

import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;

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

        Module mcpModule = McpModule.builder()
                .withAllInClass(TestingEndpoints.class)
                .withAllInClass(MapApp.class)
                .withAllInClass(DebugApp.class)
                .withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance((_) -> authenticated(new TestingIdentity("Mr. Tester"))))
                .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                .addIcon("google", binding -> binding.toInstance(new Icon("https://www.gstatic.com/images/branding/searchlogo/ico/favicon.ico")))
                .withServerIcons(ImmutableSet.of("google"))
                .build();

        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(mcpModule)
                .add(binder -> binder.bind(TestingEndpoints.class).in(SINGLETON))
                .add(binder -> binder.bind(SleepToolController.class).toInstance(SleepToolController.instance()))
                .add(new NodeModule())
                .add(new TestingHttpServerModule(LocalServer.class.getName(), port.orElse(0)))
                .add(new JaxrsModule())
                .add(new JsonModule());

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        Injector injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();

        log.info("Local server started at: %s/mcp", injector.getInstance(HttpServerInfo.class).getHttpUri());
    }
}

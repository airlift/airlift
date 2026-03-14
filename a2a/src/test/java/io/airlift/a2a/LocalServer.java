package io.airlift.a2a;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.a2a.model.AgentCapabilities;
import io.airlift.a2a.model.AgentCard;
import io.airlift.a2a.model.AgentInterface;
import io.airlift.a2a.model.AgentSkill;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.node.NodeModule;

import java.util.List;
import java.util.Optional;

public class LocalServer
{
    private LocalServer() {}

    private static final Logger log = Logger.get(LocalServer.class);

    static void main(String[] args)
    {
        Optional<Integer> port = switch (args.length) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(Integer.parseInt(args[0]));
            default -> {
                System.err.println("Usage: LocalServer [port]");
                yield Optional.empty();
            }
        };

        AgentInterface agentInterface = new AgentInterface("http://localhost:8080/a2a", "JSONRPC", Optional.empty(), "0.3");
        AgentCapabilities capabilities = new AgentCapabilities(false, false, Optional.empty(), false);
        List<String> defaultInputModes = ImmutableList.of("text/html");
        List<String> defaultOutputModes = ImmutableList.of("text/html");
        List<AgentSkill> skills = ImmutableList.of(new AgentSkill("academic-research", "Academic Research Assistant", "Provides research assistance with citations and source verification", ImmutableList.of("research", "citations", "academic"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        AgentCard agentCard = new AgentCard("test", "A test", ImmutableList.of(agentInterface), Optional.empty(), "1.0.0", Optional.empty(), capabilities, Optional.empty(), Optional.empty(), defaultInputModes, defaultOutputModes, skills, Optional.empty(), Optional.empty());

        Module a2aModule = A2aModule.builder()
                .withAgentCardSupplier(binder -> binder.toInstance(_ -> agentCard))
                .build();

        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(a2aModule)
                .add(new NodeModule())
                .add(new TestingHttpServerModule(LocalServer.class.getName(), port.orElse(0)))
                .add(new JaxrsModule())
                .add(new JsonModule());

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        Injector injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();

        log.info("Local server started at: %s", injector.getInstance(HttpServerInfo.class).getHttpUri());
    }
}

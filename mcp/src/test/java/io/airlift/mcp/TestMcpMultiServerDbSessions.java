package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Module;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.tasks.SessionTaskController;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.google.inject.Scopes.SINGLETON;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.ALERT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.DEBUG;
import static io.modelcontextprotocol.spec.McpSchema.Role.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcpMultiServerDbSessions
{
    private static final int SERVER_QTY = 3;
    private static final int CLIENT_QTY = 3;

    private final Closer closer = Closer.create();
    private final TestingDatabaseServer databaseServer;
    private final List<Client> clients;

    private record Client(McpSyncClient mcpClient, String name, List<String> logs, List<String> progress) {}

    public TestMcpMultiServerDbSessions()
    {
        McpIdentityMapper identityMapper = _ -> new McpIdentity.Authenticated<>("yep");

        databaseServer = new TestingDatabaseServer();
        TestingDbSessionController.createSchema(databaseServer);
        closer.register(databaseServer);

        Module testModule = binder -> binder.bind(TestingDatabaseServer.class).toInstance(databaseServer);

        List<TestingServer> testingServers = IntStream.range(0, SERVER_QTY)
                .mapToObj(_ -> new TestingServer(Optional.of(testModule), builder -> builder
                        .withIdentityMapper(McpIdentity.class, binding -> binding.toInstance(identityMapper))
                        .withSessions(binding -> binding.to(TestingDbSessionController.class).in(SINGLETON))
                        .withTasks(binding -> binding.to(SessionTaskController.class).in(SINGLETON))
                        .build()))
                .toList();
        testingServers.forEach(closer::register);

        clients = IntStream.range(0, CLIENT_QTY)
                .mapToObj(i -> buildClient(testingServers, "Client" + i))
                .toList();
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        closer.close();
    }

    @Test
    public void testLogging()
    {
        Client firstClient = clients.getFirst();
        firstClient.mcpClient.setLoggingLevel(DEBUG);

        firstClient.mcpClient.callTool(new McpSchema.CallToolRequest("log", ImmutableMap.of()));

        firstClient.mcpClient.setLoggingLevel(ALERT);
        firstClient.mcpClient.callTool(new McpSchema.CallToolRequest("log", ImmutableMap.of()));
        System.out.println();
    }

    @Test
    public void testElicitationThenSampling()
    {
        clients.forEach(client -> {
            McpSchema.CallToolResult callToolResult = client.mcpClient.callTool(new McpSchema.CallToolRequest("elicitationThenSample", ImmutableMap.of()));
            assertThat(callToolResult.content())
                    .hasSize(1)
                    .first()
                    .asInstanceOf(type(McpSchema.TextContent.class))
                    .extracting(McpSchema.TextContent::text)
                    .isEqualTo("Go ahead: %s, %ssky".formatted(client.name, client.name));
        });
    }

    private Client buildClient(List<TestingServer> testingServers, String name)
    {
        HttpClientStreamableHttpTransport clientTransport = HttpClientStreamableHttpTransport.builder("http://localhost:12345/dummy")
                .httpRequestCustomizer((builder, _, endpoint, _, _) -> {
                    URI randomServerUri = randomServer(testingServers);
                    builder.uri(UriBuilder.fromUri(endpoint).host(randomServerUri.getHost()).port(randomServerUri.getPort()).build());
                })
                .build();

        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> progress = new CopyOnWriteArrayList<>();

        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMinutes(1))
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(_ -> new McpSchema.ElicitResult(ACCEPT, ImmutableMap.of("firstName", name, "lastName", name + "sky")))
                .sampling(createMessageRequest -> new McpSchema.CreateMessageResult(USER, new McpSchema.TextContent("Go ahead: " + ((McpSchema.TextContent) createMessageRequest.messages().getFirst().content()).text()), "dummy", null))
                .progressConsumer(progressNotification -> progress.add(progressNotification.message()))
                .loggingConsumer(loggingNotification -> logs.add(loggingNotification.data()))
                .build();
        client.initialize();

        closer.register(client::closeGracefully);

        return new Client(client, name, logs, progress);
    }

    private URI randomServer(List<TestingServer> testingServers)
    {
        return testingServers.get(ThreadLocalRandom.current().nextInt(testingServers.size()))
                .injector()
                .getInstance(TestingHttpServer.class)
                .getBaseUrl();
    }
}

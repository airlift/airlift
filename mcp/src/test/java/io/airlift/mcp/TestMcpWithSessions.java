package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.inject.Key;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.mcp.TestMcp.IDENTITY_HEADER;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.ALERT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.DEBUG;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.EMERGENCY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcpWithSessions
{
    private final Closer closer = Closer.create();
    private final Client client1;
    private final Client client2;
    private final MemorySessionController sessionController;
    private final HttpClient httpClient;
    private final String baseUri;

    private record Client(McpSyncClient mcpClient, List<String> logs, List<String> progress) {}

    public TestMcpWithSessions()
    {
        TestingServer testingServer = new TestingServer(Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                .build());
        closer.register(testingServer);

        baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();

        client1 = buildClient("Client 1");
        client2 = buildClient("Client 2");

        sessionController = (MemorySessionController) testingServer.injector().getInstance(SessionController.class);
        httpClient = testingServer.injector().getInstance(Key.get(HttpClient.class, ForTest.class));
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        closer.close();
    }

    @AfterEach
    public void reset()
    {
        client1.logs.clear();
        client2.progress.clear();
        client1.progress.clear();
        client2.progress.clear();
    }

    @Test
    public void testLogging()
    {
        client1.mcpClient.setLoggingLevel(EMERGENCY);
        client2.mcpClient.setLoggingLevel(EMERGENCY);

        client1.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        client2.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).isEmpty();
        assertThat(client2.logs).isEmpty();

        client1.mcpClient.setLoggingLevel(ALERT);
        client2.mcpClient.setLoggingLevel(DEBUG);

        client1.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).containsExactly("This is alert");
        assertThat(client2.logs).isEmpty();

        client2.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).containsExactly("This is alert");
        assertThat(client2.logs).containsExactlyInAnyOrder("This is alert", "This is debug");
    }

    @Test
    public void testProgress()
    {
        List<String> expectedProgress = IntStream.rangeClosed(0, 100)
                .mapToObj(i -> "Progress " + i + "%")
                .collect(toImmutableList());

        client1.mcpClient.callTool(new CallToolRequest("progress", ImmutableMap.of()));
        assertThat(client1.progress).isEqualTo(expectedProgress);
        assertThat(client2.progress).isEmpty();

        client2.mcpClient.callTool(new CallToolRequest("progress", ImmutableMap.of()));
        assertThat(client1.progress).isEqualTo(expectedProgress);
        assertThat(client2.progress).isEqualTo(expectedProgress);
    }

    @Test
    public void testExpiredSession()
    {
        Set<SessionId> preSessionIds = sessionController.sessionIds();
        Client client = buildClient("testExpiredSession");
        Set<SessionId> postSessionIds = sessionController.sessionIds();
        SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

        // simulate session expiring
        sessionController.deleteSession(clientSessionId);

        assertThatThrownBy(client.mcpClient::listTools)
                .hasMessageContaining("HTTP 404 Not Found");
    }

    @Test
    public void testDeleteSession()
    {
        Set<SessionId> preSessionIds = sessionController.sessionIds();
        Client client = buildClient("testDeleteSession");
        Set<SessionId> postSessionIds = sessionController.sessionIds();
        SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

        Request request = prepareDelete()
                .setUri(URI.create(baseUri + "/mcp"))
                .addHeader(MCP_SESSION_ID, clientSessionId.id())
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .build();
        httpClient.execute(request, createStatusResponseHandler());

        assertThatThrownBy(client.mcpClient::listTools)
                .hasMessageContaining("HTTP 404 Not Found");
    }

    private Client buildClient(String name)
    {
        HttpClientStreamableHttpTransport clientTransport = HttpClientStreamableHttpTransport.builder(baseUri)
                .customizeRequest(builder -> builder.header(IDENTITY_HEADER, EXPECTED_IDENTITY))
                .build();

        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> progress = new CopyOnWriteArrayList<>();

        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMinutes(1))
                .capabilities(ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(_ -> new ElicitResult(ACCEPT, ImmutableMap.of("name", name, "comments", "this is " + name)))
                .progressConsumer(progressNotification -> progress.add(progressNotification.message()))
                .loggingConsumer(loggingNotification -> logs.add(loggingNotification.data()))
                .build();
        client.initialize();

        closer.register(client::closeGracefully);

        return new Client(client, logs, progress);
    }
}

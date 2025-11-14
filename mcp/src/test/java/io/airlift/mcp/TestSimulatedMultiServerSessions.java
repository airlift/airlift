package io.airlift.mcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.memory.MemorySession;
import io.airlift.mcp.session.memory.MemorySessionConfig;
import io.airlift.mcp.session.memory.MemorySessionController;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestSimulatedMultiServerSessions
{
    private final List<TestingServer> servers;
    private final SimpleLoadBalancer loadBalancer;
    private final McpSyncClient mcpClient;
    private final Cache<String, MemorySession> cache;

    public TestSimulatedMultiServerSessions()
    {
        MemorySessionConfig memorySessionConfig = new MemorySessionConfig();
        cache = CacheBuilder.newBuilder()
                .expireAfterAccess(memorySessionConfig.getSessionTimeout().toJavaTime())
                .build();

        McpIdentityMapper identityMapper = _ -> new McpIdentity.Authenticated<>("yep");

        servers = IntStream.range(0, 3)
                .mapToObj(_ -> new TestingServer(identityMapper, binder -> binder.bind(McpSessionController.class).toInstance(new MemorySessionController(cache))))
                .collect(toImmutableList());

        List<URI> serverUris = servers.stream()
                .map(server -> server.injector().getInstance(TestingHttpServer.class).getBaseUrl())
                .collect(toImmutableList());

        loadBalancer = new SimpleLoadBalancer(serverUris);

        HttpClientStreamableHttpTransport clientTransport = HttpClientStreamableHttpTransport.builder(loadBalancer.getBaseUri().toString())
                .build();

        mcpClient = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMinutes(1))
                .capabilities(ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(this::elicitationHandler)
                .progressConsumer(this::consumeProgress)
                .loggingConsumer(this::consumeLog)
                .build();
        mcpClient.initialize();
    }

    @AfterAll
    public void shutdown()
    {
        mcpClient.close();
        servers.forEach(TestingServer::close);
        loadBalancer.close();
    }

    @Test
    public void test()
    {
        McpSchema.CallToolResult elicitationTest = mcpClient.callTool(new McpSchema.CallToolRequest("elicitationTest", ImmutableMap.of()));
        System.out.println();

        McpSchema.CallToolResult rootsTest = mcpClient.callTool(new McpSchema.CallToolRequest("showCurrentRoots", ImmutableMap.of()));
        System.out.println();

        mcpClient.addRoot(new McpSchema.Root("file://newRoot.txt", "test"));
        rootsTest = mcpClient.callTool(new McpSchema.CallToolRequest("showCurrentRoots", ImmutableMap.of()));
        rootsTest = mcpClient.callTool(new McpSchema.CallToolRequest("showCurrentRoots", ImmutableMap.of()));
        System.out.println();

        mcpClient.callTool(new McpSchema.CallToolRequest("logging", ImmutableMap.of()));
        System.out.println();
    }

    private void consumeProgress(McpSchema.ProgressNotification progressNotification)
    {
    }

    private ElicitResult elicitationHandler(ElicitRequest elicitRequest)
    {
        return new ElicitResult(ACCEPT, ImmutableMap.of("name", "something", "comments", "no comments"));
    }

    private void consumeLog(McpSchema.LoggingMessageNotification loggingMessageNotification)
    {
        System.out.println();
    }
}

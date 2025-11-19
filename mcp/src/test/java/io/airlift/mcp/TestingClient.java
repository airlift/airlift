package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static java.util.Objects.requireNonNull;

public record TestingClient(McpSyncClient mcpClient, List<String> logs, List<String> progress)
        implements Closeable
{
    public TestingClient
    {
        requireNonNull(mcpClient, "mcpClient is null");
        requireNonNull(progress, "progress is null");   // do not copy
        requireNonNull(logs, "logs is null");   // do not copy
    }

    @Override
    public void close()
    {
        mcpClient.close();
    }

    public void reset()
    {
        logs.clear();
        progress.clear();
    }

    public static TestingClient buildClient(Closer closer, String baseUri, String name)
    {
        return buildClient(closer, baseUri, name, EXPECTED_IDENTITY);
    }

    public static TestingClient buildClient(Closer closer, String baseUri, String name, String idToken)
    {
        HttpClientStreamableHttpTransport clientTransport = HttpClientStreamableHttpTransport.builder(baseUri)
                .customizeRequest(builder -> builder.header(IDENTITY_HEADER, idToken))
                .build();

        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> progress = new CopyOnWriteArrayList<>();

        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMinutes(1))
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(_ -> new McpSchema.ElicitResult(ACCEPT, ImmutableMap.of("name", name, "comments", "this is " + name)))
                .loggingConsumer(loggingNotification -> logs.add(loggingNotification.data()))
                .progressConsumer(progressNotification -> progress.add(progressNotification.message()))
                .build();
        client.initialize();

        closer.register(client::closeGracefully);

        return new TestingClient(client, logs, progress);
    }
}

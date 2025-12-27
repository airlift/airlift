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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static java.util.Objects.requireNonNull;

public record TestingClient(String name, McpSyncClient mcpClient, List<String> logs, List<String> progress, BlockingQueue<String> changes)
        implements Closeable
{
    public TestingClient
    {
        requireNonNull(name, "name is null");
        requireNonNull(mcpClient, "mcpClient is null");
        requireNonNull(progress, "progress is null");   // do not copy
        requireNonNull(logs, "logs is null");   // do not copy
        requireNonNull(changes, "changes is null");   // do not copy
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
        changes.clear();
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
        BlockingQueue<String> changes = new LinkedBlockingQueue<>();

        // can't record resource subscription changes until https://github.com/modelcontextprotocol/java-sdk/pull/735 is accepted/merged

        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMinutes(1))
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(_ -> new McpSchema.ElicitResult(ACCEPT, ImmutableMap.of("firstName", name, "lastName", name + "sky")))
                .loggingConsumer(loggingNotification -> logs.add(loggingNotification.data()))
                .progressConsumer(progressNotification -> progress.add(progressNotification.message()))
                .toolsChangeConsumer(_ -> changes.add("tools"))
                .promptsChangeConsumer(_ -> changes.add("prompts"))
                .resourcesChangeConsumer(resources -> resources.forEach(resource -> changes.add(resource.uri())))
                .build();
        client.initialize();

        closer.register(client::closeGracefully);

        return new TestingClient(name, client, logs, progress, changes);
    }
}

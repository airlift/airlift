package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Key;
import io.airlift.http.client.Request;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.SentMessages.SentMessage;
import io.airlift.mcp.sessions.ForSessionCaching;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.mcp.TestingClient.buildClient;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.model.Constants.HEADER_LAST_EVENT_ID;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestSentMessages
{
    private final Closer closer = Closer.create();
    private final TestingClient client;
    private final TestingServer testingServer;
    private final MemorySessionController sessionController;

    public TestSentMessages()
    {
        // implicitly disable pings
        Map<String, String> properties = ImmutableMap.of(
                "mcp.event-streaming.ping-threshold", "7d",
                "mcp.resource-version.update-interval", "1ms");

        testingServer = new TestingServer(properties, Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();

        client = buildClient(closer, baseUri, "client");

        sessionController = (MemorySessionController) testingServer.injector().getInstance(Key.get(SessionController.class, ForSessionCaching.class));
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        closer.close();
    }

    @Test
    public void testAdditionalMessages()
    {
        int totalMessages = 1234;
        int maxMessages = 100;
        int grouping = 12;

        SentMessages sentMessages = new SentMessages();

        int id = 0;
        while (id < totalMessages) {
            List<SentMessage> messages = new ArrayList<>();
            for (int j = 0; (j < grouping) && (id < totalMessages); ++j, ++id) {
                messages.add(new SentMessage("id-" + id, "dummy"));
            }
            sentMessages = sentMessages.withAdditionalMessages(messages, maxMessages);
        }

        assertThat(sentMessages.messages()).hasSize(maxMessages);

        List<SentMessage> expected = new ArrayList<>();
        for (int i = totalMessages - maxMessages; i < totalMessages; ++i) {
            expected.add(new SentMessage("id-" + i, "dummy"));
        }

        assertThat(sentMessages.messages()).isEqualTo(expected);
    }

    @Test
    public void testReplayEvents()
            throws Exception
    {
        SessionId sessionId = sessionController.sessionIds().iterator().next();

        // will cause list change events on the next GET
        client.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "tools")));
        client.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "prompts")));
        client.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "resources")));

        List<SentMessage> messages = new ArrayList<>();

        Future<?> future = null;

        try {
            BlockingQueue<SentMessage> queue = new LinkedBlockingQueue<>();
            future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> eventStream(sessionId, Optional.empty(), queue));

            for (int i = 0; i < 3; ++i) {
                SentMessage message = queue.poll(10, SECONDS);
                assertThat(message).isNotNull();
                messages.add(message);
            }
        }
        finally {
            if (future != null) {
                future.cancel(true);
                future = null;
            }
        }

        List<SentMessage> replayedMessages = new ArrayList<>();

        try {
            BlockingQueue<SentMessage> queue = new LinkedBlockingQueue<>();
            future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> eventStream(sessionId, Optional.of(messages.getFirst().id()), queue));

            for (int i = 0; i < 2; ++i) {
                SentMessage message = queue.poll(10, SECONDS);
                assertThat(message).isNotNull();
                replayedMessages.add(message);
            }
        }
        finally {
            if (future != null) {
                future.cancel(true);
            }
        }

        assertThat(replayedMessages).isEqualTo(messages.subList(1, messages.size()));
    }

    private void eventStream(SessionId sessionId, Optional<String> lastMessageId, Queue<SentMessage> queue)
    {
        URI uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");

        Request.Builder builder = prepareGet()
                .setUri(uri)
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .addHeader(MCP_SESSION_ID, sessionId.id());
        lastMessageId.ifPresent(id -> builder.addHeader(HEADER_LAST_EVENT_ID, id));
        Request request = builder.build();

        try (StreamingResponse streamingResponse = testingServer.httpClient().executeStreaming(request)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(streamingResponse.getInputStream()));

            while (!Thread.currentThread().isInterrupted()) {
                String idLine = reader.readLine();
                String dataLine = reader.readLine();
                String blankLine = reader.readLine();

                assertThat(idLine).startsWith("id: ");
                assertThat(dataLine).startsWith("data: ");
                assertThat(blankLine).isEmpty();

                queue.add(new SentMessage(idLine.substring("id: ".length()), dataLine.substring("data: ".length())));
            }
        }
        catch (Exception e) {
            if (getRootCause(e) instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            else {
                throw new RuntimeException(e);
            }
        }
    }
}

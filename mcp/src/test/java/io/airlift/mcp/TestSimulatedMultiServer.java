package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.sessions.SessionId;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestSimulatedMultiServer
{
    private static final int SERVER_COUNT = 3;

    private final Closer closer = Closer.create();
    private final TestingClient client;
    private final Set<SessionId> sessionIds = Sets.newConcurrentHashSet();
    private final List<Injector> injectors;

    public TestSimulatedMultiServer()
    {
        TestingDatabaseServer databaseServer = closer.register(new TestingDatabaseServer());

        Module module = binder -> binder.bind(TestingDatabaseServer.class).toInstance(databaseServer);

        injectors = IntStream.range(0, SERVER_COUNT)
                .mapToObj(_ -> closer.register(buildTestingServer(module)).injector())
                .collect(toImmutableList());

        List<URI> uris = injectors.stream()
                .map(injector -> injector.getInstance(HttpServerInfo.class).getHttpUri())
                .map(uri -> uri.resolve("/mcp"))
                .collect(toImmutableList());

        McpClientTransport transport = buildTransport(uris);

        client = TestingClient.buildClient(closer, "SimulatedMultiServer", transport);
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
        client.reset();
    }

    @RepeatedTest(5)
    public void testElicitation()
    {
        // because of RoundRobinRequestBuilder - the elicitation response will go to
        // a different server than the one that is processing the request
        TestMcp.testElicitation(client);

        assertThat(sessionIds).hasSize(1);
    }

    @RepeatedTest(5)
    public void testCancellation()
            throws Exception
    {
        // ensure that the next request goes to the first server
        RoundRobinRequestBuilder.resetCounter();

        // call the sleep() tool that will sleep for 1 day (i.e. forever for our purposes)
        Future<McpSchema.CallToolResult> future = newVirtualThreadPerTaskExecutor().submit(() ->
                client.mcpClient().callTool(new McpSchema.CallToolRequest("sleep", ImmutableMap.of("name", "dummy", "secondsToSleep", (int) TimeUnit.DAYS.toSeconds(1)))));

        Injector firstInjector = injectors.getFirst();
        SleepToolController sleepToolController = firstInjector.getInstance(SleepToolController.class);
        assertThat(sleepToolController.startedLatch().tryAcquire(5, SECONDS)).isTrue();

        Collection<Object> activeRequestIds = firstInjector.getInstance(CancellationController.class).activeRequestIds();
        assertThat(activeRequestIds).hasSize(1);
        Object requestId = activeRequestIds.iterator().next();

        Injector lastInjector = injectors.getLast();

        assertThat(firstInjector).isNotSameAs(lastInjector);

        // send a cancellation notification to a different server than the one processing the request
        URI uri = lastInjector.getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
        HttpClient httpClient = lastInjector.getInstance(Key.get(HttpClient.class, ForTest.class));
        CancelledNotification cancelledNotification = new CancelledNotification(requestId, Optional.of("Just because"));
        JsonRpcRequest<CancelledNotification> jsonRpcRequest = JsonRpcRequest.buildNotification(NOTIFICATION_CANCELLED, cancelledNotification);

        assertThat(sessionIds).hasSize(1);

        Request request = preparePost().setUri(uri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .addHeader(MCP_SESSION_ID, sessionIds.iterator().next().id())
                .setBodyGenerator(jsonBodyGenerator(jsonCodec(new TypeToken<>() {}), jsonRpcRequest))
                .build();
        httpClient.execute(request, createStatusResponseHandler());

        McpSchema.CallToolResult result = future.get(5, SECONDS);
        assertThat(result.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(McpSchema.TextContent.class))
                .extracting(McpSchema.TextContent::text)
                .isEqualTo("interrupted");  // proves that the sleep was indeed cancelled
    }

    private McpClientTransport buildTransport(List<URI> uris)
    {
        BiConsumer<String, String> headerConsumer = (name, value) -> {
            if (name.equalsIgnoreCase(Constants.HEADER_SESSION_ID)) {
                sessionIds.add(new SessionId(value));
            }
        };

        return HttpClientStreamableHttpTransport.builder(uris.getFirst().toString())
                .requestBuilder(new RoundRobinRequestBuilder(HttpRequest.newBuilder(), uris, headerConsumer))
                .customizeRequest(builder -> builder.header(IDENTITY_HEADER, EXPECTED_IDENTITY))
                .build();
    }

    private static TestingServer buildTestingServer(Module module)
    {
        return new TestingServer(ImmutableMap.of(), Optional.of(module), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withSessions(binding -> binding.to(TestingDatabaseSessionController.class))
                .build());
    }
}

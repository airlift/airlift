package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.json.JsonCodecFactory;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.sessions.ForSessionCaching;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteArgument;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.TestMcp.Mode.DATABASE_SESSIONS;
import static io.airlift.mcp.TestingClient.buildClient;
import static io.airlift.mcp.TestingIdentityMapper.ERRORED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.RESOURCE_NOT_FOUND;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.ALERT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.DEBUG;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.EMERGENCY;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class TestMcp
{
    private static final Logger log = Logger.get(TestMcp.class);

    private final Closer closer = Closer.create();
    private final TestingClient client1;
    private final TestingClient client2;
    private final String baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TestingServer testingServer;
    private final Supplier<Set<SessionId>> sessionIdsSupplier;
    private final Consumer<SessionId> sessionDeleter;

    public enum Mode
    {
        MEMORY_SESSIONS,
        DATABASE_SESSIONS,
    }

    protected TestMcp(Mode mode)
    {
        Module module = binder -> {
            if (mode == DATABASE_SESSIONS) {
                binder.bind(TestingDatabaseServer.class).in(SINGLETON);
            }
        };

        Map<String, String> properties = ImmutableMap.of("mcp.http-get-events.enabled", "false");

        testingServer = new TestingServer(properties, Optional.of(module), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withSessions(binding -> binding.to((mode == DATABASE_SESSIONS) ? TestingDatabaseSessionController.class : MemorySessionController.class).in(SINGLETON))
                .addIcon("google", binding -> binding.toInstance(new Icon("https://www.gstatic.com/images/branding/searchlogo/ico/favicon.ico")))
                .withAllInClass(TestingEndpoints.class)
                .build());
        closer.register(testingServer);

        baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();

        client1 = buildClient(closer, baseUri, "Client 1");
        client2 = buildClient(closer, baseUri, "Client 2");

        httpClient = testingServer.httpClient();
        objectMapper = testingServer.injector().getInstance(ObjectMapper.class);

        SessionController sessionController = testingServer.injector().getInstance(Key.get(SessionController.class, ForSessionCaching.class));

        sessionIdsSupplier = switch (mode) {
            case MEMORY_SESSIONS -> () -> ((MemorySessionController) sessionController).sessionIds();
            case DATABASE_SESSIONS -> () -> ((TestingDatabaseSessionController) sessionController).sessionIds();
        };
        sessionDeleter = sessionController::deleteSession;
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
        Stream.of(client1, client2).forEach(TestingClient::reset);
    }

    @Test
    public void testBadAuth()
    {
        assertThatThrownBy(() -> buildClient(closer, baseUri, "no-token", ""))
                .rootCause()
                .hasMessageContaining("\"status\":\"401\"")
                .hasMessageContaining("Empty or missing identity header");

        assertThatThrownBy(() -> buildClient(closer, baseUri, "invalid-token", "Invalid Identity"))
                .rootCause()
                .hasMessageContaining("\"status\":\"403\"")
                .hasMessageContaining("Identity Invalid Identity is not authorized to access");

        assertThatThrownBy(() -> buildClient(closer, baseUri, "error-token", ERRORED_IDENTITY))
                .rootCause()
                .hasMessageContaining("\"code\":" + INTERNAL_ERROR.code())
                .hasMessageContaining("\"message\":\"This identity cannot catch a break");
    }

    @Test
    public void testInvalidRpcRequests()
    {
        io.airlift.mcp.model.CallToolRequest callToolRequest = new io.airlift.mcp.model.CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);

        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(() -> objectMapper);

        URI uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");

        // missing proper Accept header
        Request request = preparePost().setUri(uri)
                .addHeader("Content-Type", "application/json")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        FullJsonResponseHandler.JsonResponse<Object> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .contains("\"message\":\"Both application/json and text/event-stream required in Accept header\"");

        // nonsensical object in body
        request = preparePost().setUri(uri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader(IDENTITY_HEADER, "Mr. Tester")
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<>() {}), new io.airlift.mcp.model.ListToolsResult(ImmutableList.of())))
                .build();
        response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .contains("\"message\":\"Cannot deserialize JsonRpcMessage");
    }

    @Test
    public void testLogging()
    {
        client1.mcpClient().setLoggingLevel(EMERGENCY);
        client2.mcpClient().setLoggingLevel(EMERGENCY);

        client1.mcpClient().callTool(new CallToolRequest("log", ImmutableMap.of()));
        client2.mcpClient().callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(takeNFromQueue(client1.logs(), 1)).isEmpty();
        assertThat(takeNFromQueue(client2.logs(), 1)).isEmpty();

        client1.mcpClient().setLoggingLevel(ALERT);
        client2.mcpClient().setLoggingLevel(DEBUG);

        client1.mcpClient().callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(takeNFromQueue(client1.logs(), 2)).containsExactly("This is alert");
        assertThat(client2.logs()).isEmpty();

        client2.mcpClient().callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(takeNFromQueue(client1.logs(), 1)).isEmpty();
        assertThat(takeNFromQueue(client2.logs(), 2)).containsExactlyInAnyOrder("This is alert", "This is debug");
    }

    @Test
    public void testProgress()
    {
        List<String> expectedProgress = IntStream.rangeClosed(0, 100)
                .mapToObj(i -> "Progress " + i + "%")
                .collect(toImmutableList());

        client1.mcpClient().callTool(new CallToolRequest("progress", ImmutableMap.of()));
        assertThat(takeNFromQueue(client1.progress(), 101)).isEqualTo(expectedProgress);
        assertThat(takeNFromQueue(client2.progress(), 1)).isEmpty();

        client2.mcpClient().callTool(new CallToolRequest("progress", ImmutableMap.of()));
        assertThat(takeNFromQueue(client1.progress(), 1)).isEmpty();
        assertThat(takeNFromQueue(client2.progress(), 101)).isEqualTo(expectedProgress);
    }

    @Test
    public void testCompletions()
    {
        CompleteRequest completeRequest = new CompleteRequest(new PromptReference("greeting"), new CompleteArgument("name", "Jo"));
        CompleteResult completeResult = client1.mcpClient().completeCompletion(completeRequest);
        assertThat(completeResult.completion().values())
                .hasSize(1)
                .first()
                .asInstanceOf(type(String.class))
                .isEqualTo("Jordan");

        completeRequest = new CompleteRequest(new ResourceReference("file://{id}.template"), new CompleteArgument("id", "m"));
        completeResult = client1.mcpClient().completeCompletion(completeRequest);
        assertThat(completeResult.completion().values())
                .hasSize(2)
                .asInstanceOf(list(String.class))
                .containsExactlyInAnyOrder("manny", "moe");
    }

    @Test
    public void testTools()
    {
        ListToolsResult listToolsResult = client1.mcpClient().listTools();
        assertThat(listToolsResult.tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("add", "throws", "addThree", "addFirstTwoAndAllThree", "progress", "log", "setVersion", "sleep", "elicitation", "sampling", "roots");

        CallToolResult callToolResult = client1.mcpClient().callTool(new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2)));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("3");
    }

    @Test
    public void testToolPrimitiveStructuredContent()
    {
        ListToolsResult listToolsResult = client1.mcpClient().listTools();
        assertThat(listToolsResult.tools())
                .filteredOn(tool -> tool.name().equals("addThree"))
                .hasSize(1)
                .first()
                .extracting(Tool::outputSchema)
                .satisfies(outputSchema -> assertThat(outputSchema).isNull());

        CallToolRequest callToolRequest = new CallToolRequest("addThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
        CallToolResult callToolResult = client1.mcpClient().callTool(callToolRequest);
        assertThat(callToolResult.structuredContent()).isNull();
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("6");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testToolEmbeddedStructuredContent()
    {
        ListToolsResult listToolsResult = client1.mcpClient().listTools();
        assertThat(listToolsResult.tools())
                .filteredOn(tool -> tool.name().equals("addFirstTwoAndAllThree"))
                .hasSize(1)
                .first()
                .extracting(Tool::outputSchema)
                .satisfies(outputSchema -> {
                    assertThat(outputSchema.get("type")).isEqualTo("object");

                    assertThat(outputSchema.get("required"))
                            .isNotNull()
                            .asInstanceOf(type(List.class))
                            .satisfies(list -> assertThat(list).containsExactlyInAnyOrder("firstTwo", "allThree"));

                    assertThat(outputSchema.get("properties")).isNotNull();
                    Map<String, Object> properties = (Map<String, Object>) outputSchema.get("properties");
                    assertThat(properties.keySet()).containsExactlyInAnyOrder("firstTwo", "allThree");

                    assertThat(((Map<String, Object>) properties.get("firstTwo")).get("type")).isEqualTo("integer");
                    assertThat(((Map<String, Object>) properties.get("allThree")).get("type")).isEqualTo("integer");
                });

        CallToolRequest callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
        CallToolResult twoAndThreeCallToolResult = client1.mcpClient().callTool(callToolRequest);
        assertThat(twoAndThreeCallToolResult.isError()).isFalse();
        assertThat(twoAndThreeCallToolResult.structuredContent())
                .isNotNull()
                .isEqualTo(ImmutableMap.of("firstTwo", 3, "allThree", 6));

        // Test not sending an optional parameter
        callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2));
        twoAndThreeCallToolResult = client1.mcpClient().callTool(callToolRequest);
        assertThat(twoAndThreeCallToolResult.isError()).isFalse();
        assertThat(twoAndThreeCallToolResult.structuredContent())
                .isNotNull()
                .isEqualTo(ImmutableMap.of("firstTwo", 3, "allThree", 3));

        // Test the "error" path
        callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", -1, "b", -2, "c", -3));
        twoAndThreeCallToolResult = client1.mcpClient().callTool(callToolRequest);
        assertThat(twoAndThreeCallToolResult.isError()).isTrue();
        assertThat(twoAndThreeCallToolResult.structuredContent()).isNull();
        assertThat(twoAndThreeCallToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("Negative numbers are not allowed");
    }

    @Test
    public void testExceptionWrapping()
    {
        CallToolRequest callToolRequest = new CallToolRequest("throws", ImmutableMap.of());
        CallToolResult callToolResult = client1.mcpClient().callTool(callToolRequest);
        assertThat(callToolResult.isError()).isTrue();
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("this ain't good");
    }

    @Test
    public void testGetMcpReturns405()
    {
        URI uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(() -> objectMapper);

        Request request = prepareGet()
                .setUri(uri)
                .addHeader("Accept", "application/json,text/event-stream")
                .build();

        var response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_UNAUTHORIZED);

        request = prepareGet()
                .setUri(uri)
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .build();

        response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void testPrompts()
    {
        ListPromptsResult listPromptsResult = client1.mcpClient().listPrompts();
        assertThat(listPromptsResult.prompts())
                .extracting(Prompt::name)
                .containsExactlyInAnyOrder("greeting", "age");

        GetPromptResult getPromptResult = client1.mcpClient().getPrompt(new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt")));
        assertThat(getPromptResult.messages())
                .hasSize(1)
                .first()
                .extracting(PromptMessage::content)
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("Hello, Galt!");
    }

    @Test
    public void testResources()
    {
        ListResourcesResult listResourcesResult = client1.mcpClient().listResources();
        assertThat(listResourcesResult.resources())
                .extracting(Resource::name)
                .containsExactlyInAnyOrder("example1", "example2");

        ReadResourceRequest readResourceRequest = new ReadResourceRequest("file://example2.txt");
        ReadResourceResult readResourceResult = client1.mcpClient().readResource(readResourceRequest);
        assertThat(readResourceResult.contents())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextResourceContents.class))
                .extracting(TextResourceContents::text)
                .isEqualTo("This is the content of file://example2.txt");

        readResourceRequest = new ReadResourceRequest("file://test.template");
        readResourceResult = client1.mcpClient().readResource(readResourceRequest);
        assertThat(readResourceResult.contents())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextResourceContents.class))
                .extracting(TextResourceContents::text)
                .isEqualTo("ID is: test");

        ReadResourceRequest badReadResourceRequest = new ReadResourceRequest("file://not-a-template");
        assertThatThrownBy(() -> client1.mcpClient().readResource(badReadResourceRequest))
                .satisfies(e -> assertMcpError(e, RESOURCE_NOT_FOUND, "Resource not found: file://not-a-template"));
    }

    @Test
    public void testExpiredSession()
    {
        Set<SessionId> preSessionIds = sessionIdsSupplier.get();
        TestingClient client = buildClient(closer, baseUri, "testExpiredSession");
        Set<SessionId> postSessionIds = sessionIdsSupplier.get();
        SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

        // simulate session expiring
        sessionDeleter.accept(clientSessionId);

        assertThatThrownBy(client.mcpClient()::listTools)
                .hasMessageContaining("HTTP 404 Not Found");
    }

    @Test
    public void testDeleteSession()
    {
        Set<SessionId> preSessionIds = sessionIdsSupplier.get();
        TestingClient client = buildClient(closer, baseUri, "testDeleteSession");
        Set<SessionId> postSessionIds = sessionIdsSupplier.get();
        SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

        Request request = prepareDelete()
                .setUri(URI.create(baseUri + "/mcp"))
                .addHeader(HttpHeaders.MCP_SESSION_ID, clientSessionId.id())
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .build();
        httpClient.execute(request, createStatusResponseHandler());

        assertThatThrownBy(client.mcpClient()::listTools)
                .hasMessageContaining("HTTP 404 Not Found");
    }

    @RepeatedTest(5)
    public void testListChangeNotifications()
            throws InterruptedException
    {
        TestingClient listChangeClient1 = buildClient(closer, baseUri, "ListChangeNotifications1");
        TestingClient listChangeClient2 = buildClient(closer, baseUri, "ListChangeNotifications2");

        listChangeClient1.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "tools")));

        TestingClient listChangeClient3 = buildClient(closer, baseUri, "ListChangeNotifications3");

        // calling listTools will also call reconcileVersions() internally
        listChangeClient1.mcpClient().listTools();
        listChangeClient2.mcpClient().listTools();
        listChangeClient3.mcpClient().listTools();

        assertChanges(listChangeClient1.changes(), 1).contains("tools");
        assertChanges(listChangeClient2.changes(), 1).contains("tools");
        assertChanges(listChangeClient3.changes(), 0).isEmpty();   // created after the version change

        listChangeClient1.changes().clear();
        listChangeClient2.changes().clear();

        listChangeClient1.mcpClient().listTools();
        listChangeClient2.mcpClient().listTools();
        listChangeClient3.mcpClient().listTools();

        assertChanges(listChangeClient1.changes(), 0).isEmpty();
        assertChanges(listChangeClient2.changes(), 0).isEmpty();
        assertChanges(listChangeClient3.changes(), 0).isEmpty();

        listChangeClient1.mcpClient().subscribeResource(new McpSchema.SubscribeRequest("file://example1.txt"));
        listChangeClient2.mcpClient().subscribeResource(new McpSchema.SubscribeRequest("file://example1.txt"));

        listChangeClient2.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "RESOURCE", "name", "example1")));
        listChangeClient2.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "tools")));
        listChangeClient2.mcpClient().callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "prompts")));

        listChangeClient1.mcpClient().listTools();
        listChangeClient2.mcpClient().listTools();
        listChangeClient3.mcpClient().listTools();

        assertChanges(listChangeClient1.changes(), 3).containsExactlyInAnyOrder("tools", "prompts", "file://example1.txt");
        assertChanges(listChangeClient2.changes(), 3).containsExactlyInAnyOrder("tools", "prompts", "file://example1.txt");
        assertChanges(listChangeClient3.changes(), 2).containsExactlyInAnyOrder("tools", "prompts");
        assertThat(listChangeClient3.changes().poll(2, SECONDS)).isNull();
    }

    @RepeatedTest(5)
    public void testCancellation()
            throws Exception
    {
        SleepToolController sleepToolController = testingServer.injector().getInstance(SleepToolController.class);
        sleepToolController.reset();

        // control run to make sure it exits as expected after sleep time

        CallToolResult callToolResult = client1.mcpClient().callTool(new CallToolRequest("sleep", ImmutableMap.of("name", "dummy", "secondsToSleep", 3)));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("timeout");

        sleepToolController.reset();

        // start multiple calls to the sleep() tool that will sleep for 1 day (i.e. forever for our purposes)

        int threadQty = 10;

        try (ExecutorService executorService = newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<CallToolResult>> runs = IntStream.range(0, threadQty)
                    .mapToObj(index -> {
                        String name = "run-" + index;
                        Callable<CallToolResult> callToolResultCallable = () -> client1.mcpClient().callTool(new CallToolRequest("sleep", ImmutableMap.of("name", name, "secondsToSleep", (int) TimeUnit.DAYS.toSeconds(1))));
                        Future<CallToolResult> future = executorService.submit(callToolResultCallable);
                        return Map.entry(name, future);
                    })
                    .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

            // wait for all the sleep() tool runs to start
            assertThat(sleepToolController.startedLatch().tryAcquire(threadQty, 5, SECONDS)).isTrue();

            // they should all be registered in the CancellationController
            Collection<Object> activeRequestIds = testingServer.injector().getInstance(CancellationController.class).activeRequestIds();
            assertThat(activeRequestIds).hasSize(threadQty);

            // choose a random run's request ID to cancel
            Object requestId = ImmutableList.copyOf(activeRequestIds).get(ThreadLocalRandom.current().nextInt(threadQty));

            // send a cancellation notification for the random run
            URI uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
            CancelledNotification cancelledNotification = new CancelledNotification(requestId, Optional.of("Just because"));
            JsonRpcRequest<CancelledNotification> jsonRpcRequest = JsonRpcRequest.buildNotification(NOTIFICATION_CANCELLED, cancelledNotification);

            // can't know which session it is - try em all
            sessionIdsSupplier.get().forEach(sessionId -> {
                Request request = preparePost().setUri(uri)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json, text/event-stream")
                        .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                        .addHeader(MCP_SESSION_ID, sessionId.id())
                        .setBodyGenerator(jsonBodyGenerator(jsonCodec(new TypeToken<>() {
                        }), jsonRpcRequest))
                        .build();
                httpClient.execute(request, createStatusResponseHandler());
            });

            // wait for one of the sleep() tool runs to exit
            String exitName = sleepToolController.namesThatHaveExited().poll(10, SECONDS);
            assertThat(exitName).isNotNull();
            log.info("Sleep tool run with name %s was cancelled", exitName);

            // figure out which thread it belongs to
            Future<CallToolResult> interruptedFuture = runs.get(exitName);
            assertThat(interruptedFuture).isNotNull();

            // the interrupted tool run should return "interrupted"
            callToolResult = interruptedFuture.get(10, SECONDS);
            assertThat(callToolResult.content())
                    .hasSize(1)
                    .first()
                    .asInstanceOf(type(TextContent.class))
                    .extracting(TextContent::text)
                    .isEqualTo("interrupted");

            // the other tool runs should still be running
            // send them all a signal via sleepToolController for them to exit
            // they should all exit with "success"
            runs.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(exitName))
                    .forEach(entry -> {
                        try {
                            assertThat(entry.getValue().isDone()).isFalse();
                            sleepToolController.namesThatShouldExit().add(entry.getKey());

                            CallToolResult result = entry.getValue().get(5, SECONDS);
                            assertThat(result.content())
                                    .hasSize(1)
                                    .first()
                                    .asInstanceOf(type(TextContent.class))
                                    .extracting(TextContent::text)
                                    .isEqualTo("success");
                        }
                        catch (Exception e) {
                            fail(e);
                        }
                    });
        }
    }

    @Test
    public void testElicitation()
    {
        CallToolResult callToolResult = client1.mcpClient().callTool(new CallToolRequest("elicitation", ImmutableMap.of()));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("Hello, " + client1.name() + " " + client1.name() + "sky!");
    }

    @Test
    public void testRoots()
    {
        CallToolResult callToolResult = client1.mcpClient().callTool(new CallToolRequest("roots", ImmutableMap.of()));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("");

        client1.mcpClient().addRoot(new McpSchema.Root("file://1", "1"));
        client1.mcpClient().addRoot(new McpSchema.Root("file://2", "2"));
        client1.mcpClient().rootsListChangedNotification();

        callToolResult = client1.mcpClient().callTool(new CallToolRequest("roots", ImmutableMap.of()));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("file://1, file://2");

        callToolResult = client2.mcpClient().callTool(new CallToolRequest("roots", ImmutableMap.of()));
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("");
    }

    private AbstractCollectionAssert<?, Collection<? extends String>, String, ObjectAssert<String>> assertChanges(BlockingQueue<String> changes, int qty)
    {
        if (qty == 0) {
            try {
                // sleep for a bit to ensure there are no changes
                // this isn't perfect but given the current API it's the best we can do
                MILLISECONDS.sleep(250);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while checking empty changes");
            }

            if (changes.isEmpty()) {
                return assertThat(ImmutableSet.of());
            }
            fail("Expected no changes, but some were found");
        }

        Set<String> result = new HashSet<>();
        while (result.size() < qty) {
            try {
                String value = changes.poll(5, SECONDS);
                if (value != null) {
                    result.add(value);
                    continue;
                }
                fail("Timed out waiting for changes");
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for changes to complete");
            }
        }
        return assertThat(result);
    }

    private void assertMcpError(Throwable throwable, int code, String message)
    {
        assertThat(throwable)
                .asInstanceOf(type(McpError.class))
                .extracting(McpError::getJsonRpcError)
                .extracting(JSONRPCError::code, JSONRPCError::message)
                .contains(code, message);
    }

    private List<String> takeNFromQueue(BlockingQueue<String> queue, int qty)
    {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        while (qty-- > 0) {
            try {
                String value = queue.poll(250, MILLISECONDS);
                if (value == null) {
                    break;
                }
                builder.add(value);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return builder.build();
    }
}

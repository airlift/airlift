package io.airlift.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.inject.Key;
import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodecFactory;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.TestingEndpoints.TwoAndThree;
import io.airlift.mcp.client.settings.ClientMode;
import io.airlift.mcp.client.settings.LoggingConsumer;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.client.settings.ProgressConsumer;
import io.airlift.mcp.client.settings.RequestFilter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteRequest.CompleteArgument;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetPromptResult.PromptMessage;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.operations.legacy.sessions.ForSessionCaching;
import io.airlift.mcp.operations.legacy.sessions.SessionController;
import io.airlift.mcp.operations.legacy.sessions.SessionId;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.McpMetadata.SKILLS_INSTRUCTIONS;
import static io.airlift.mcp.TestingIdentityMapper.ERRORED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.LOGGING_LEVEL;
import static io.airlift.mcp.client.McpClientSetting.MAX_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MIN_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.NOTIFICATION_CONSUMER;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.McpMapper.requireContentString;
import static io.airlift.mcp.client.McpMapper.requireStructuredContent;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_ONLY;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.SKILL_INDEX_URI;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.LoggingLevel.ALERT;
import static io.airlift.mcp.model.LoggingLevel.DEBUG;
import static io.airlift.mcp.model.LoggingLevel.EMERGENCY;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class TestMcpClient
        extends McpClientTestBase
{
    private final ClientMode clientMode;

    protected TestMcpClient(ClientMode clientMode)
    {
        this.clientMode = requireNonNull(clientMode, "clientMode is null");
    }

    @Test
    public void testBadAuth()
    {
        // the identity mapper rejects a missing identity with 401 and an unknown identity with 403 - the client
        // surfaces both as a failure of the first request it makes
        assertThatThrownBy(() -> {
            try (var connection = createClient(Optional.empty()).connect(uri())) {
                connection.listTools(Optional.empty());
            }
        }).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> {
            try (var connection = createClient(Optional.of("Invalid Identity")).connect(uri())) {
                connection.listTools(Optional.empty());
            }
        }).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> {
            try (var connection = createClient(Optional.of(ERRORED_IDENTITY)).connect(uri())) {
                connection.listTools(Optional.empty());
            }
        }).isInstanceOf(RuntimeException.class);

        // the status codes themselves are asserted at the HTTP level as the client does not yet surface them
        assertThat(rawPostStatus(Optional.empty())).isEqualTo(SC_UNAUTHORIZED);
        assertThat(rawPostStatus(Optional.of("Invalid Identity"))).isEqualTo(SC_FORBIDDEN);
    }

    @Test
    public void testInvalidRpcRequests()
    {
        CallToolRequest callToolRequest = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);

        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(injector().getInstance(JsonMapper.class));

        // missing proper Accept header
        Request request = preparePost().setUri(uri())
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        FullJsonResponseHandler.JsonResponse<Object> response = httpClient().execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .contains("\"message\":\"application/json is required in Accept header\"");

        // nonsensical object in body
        request = preparePost().setUri(uri())
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json, text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<>() {}), new ListToolsResult(ImmutableList.of())))
                .build();
        response = httpClient().execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .contains("\"message\":\"Cannot deserialize JsonRpcMessage");
    }

    @Test
    public void testGetMcpReturns405()
    {
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(injector().getInstance(JsonMapper.class));

        Request request = prepareGet()
                .setUri(uri())
                .addHeader(ACCEPT, "application/json,text/event-stream")
                .build();

        var response = httpClient().execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_UNAUTHORIZED);

        request = prepareGet()
                .setUri(uri())
                .addHeader(ACCEPT, "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .build();

        response = httpClient().execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void testLogging()
    {
        BlockingQueue<String> logs1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> logs2 = new LinkedBlockingQueue<>();

        try (var connection1 = createClient(EMERGENCY, logs1).connect(uri());
                var connection2 = createClient(EMERGENCY, logs2).connect(uri())) {
            connection1.callTool(new CallToolRequest("log", ImmutableMap.of()));
            connection2.callTool(new CallToolRequest("log", ImmutableMap.of()));
            assertThat(takeNFromQueue(logs1, 1)).isEmpty();
            assertThat(takeNFromQueue(logs2, 1)).isEmpty();
        }

        try (var connection1 = createClient(ALERT, logs1).connect(uri());
                var connection2 = createClient(DEBUG, logs2).connect(uri())) {
            connection1.callTool(new CallToolRequest("log", ImmutableMap.of()));
            assertThat(takeNFromQueue(logs1, 2)).containsExactly("This is alert");
            assertThat(logs2).isEmpty();

            connection2.callTool(new CallToolRequest("log", ImmutableMap.of()));
            assertThat(takeNFromQueue(logs1, 1)).isEmpty();
            assertThat(takeNFromQueue(logs2, 2)).containsExactlyInAnyOrder("This is alert", "This is debug");
        }
    }

    @Test
    public void testProgress()
    {
        List<String> expectedProgress = IntStream.rangeClosed(0, 100)
                .mapToObj(i -> "Progress " + i + "%")
                .collect(toImmutableList());

        BlockingQueue<String> progress1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> progress2 = new LinkedBlockingQueue<>();

        try (var connection1 = createProgressClient(progress1).connect(uri());
                var connection2 = createProgressClient(progress2).connect(uri())) {
            connection1.callTool(new CallToolRequest("progress", ImmutableMap.of()));
            assertThat(takeNFromQueue(progress1, 101)).isEqualTo(expectedProgress);
            assertThat(takeNFromQueue(progress2, 1)).isEmpty();

            connection2.callTool(new CallToolRequest("progress", ImmutableMap.of()));
            assertThat(takeNFromQueue(progress1, 1)).isEmpty();
            assertThat(takeNFromQueue(progress2, 101)).isEqualTo(expectedProgress);
        }
    }

    @Test
    public void testCompletions()
    {
        try (var connection = createClient().connect(uri())) {
            CompleteRequest completeRequest = new CompleteRequest(new PromptReference("greeting", Optional.empty()), new CompleteArgument("name", "Jo"), Optional.empty());
            CompleteResult completeResult = connection.completeCompletion(completeRequest);
            assertThat(completeResult.completion().values())
                    .hasSize(1)
                    .first()
                    .asInstanceOf(type(String.class))
                    .isEqualTo("Jordan");

            completeRequest = new CompleteRequest(new ResourceReference("file://{id}.template"), new CompleteArgument("id", "m"), Optional.empty());
            completeResult = connection.completeCompletion(completeRequest);
            assertThat(completeResult.completion().values())
                    .hasSize(2)
                    .asInstanceOf(list(String.class))
                    .containsExactlyInAnyOrder("manny", "moe");
        }
    }

    @Test
    public void testListTools()
    {
        try (var connection = createClient().connect(uri())) {
            ListToolsResult listToolsResult = connection.listTools(Optional.empty());
            assertThat(listToolsResult.tools())
                    .extracting(Tool::name)
                    .containsExactlyInAnyOrder("add", "throws", "addThree", "addFirstTwoAndAllThree", "progress", "log", "setVersion", "sleep", "elicitation", "sampling", "show-map", "geocode", "task_add", "task_confirm", "task_fail", "endless_input", "task_endless_input");
        }
    }

    @Test
    public void testTools()
    {
        try (var connection = createClient().connect(uri())) {
            CallToolResult callToolResult = connection.callTool(new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2)));
            assertThat(requireContentString(callToolResult)).isEqualTo("3");
        }
    }

    @Test
    public void testToolPrimitiveStructuredContent()
    {
        try (var connection = createClient().connect(uri())) {
            ListToolsResult listToolsResult = connection.listTools(Optional.empty());
            assertThat(listToolsResult.tools())
                    .filteredOn(tool -> tool.name().equals("addThree"))
                    .hasSize(1)
                    .first()
                    .extracting(Tool::outputSchema)
                    .satisfies(outputSchema -> assertThat(outputSchema).isEqualTo(Optional.empty()));

            CallToolRequest callToolRequest = new CallToolRequest("addThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
            CallToolResult callToolResult = connection.callTool(callToolRequest);
            assertThat(callToolResult.structuredContent()).isEmpty();
            assertThat(requireContentString(callToolResult)).isEqualTo("6");
        }
    }

    @Test
    public void testToolEmbeddedStructuredContent()
    {
        try (var connection = createClient().connect(uri())) {
            ListToolsResult listToolsResult = connection.listTools(Optional.empty());
            Tool addFirstTwoAndAllThree = listToolsResult.tools().stream()
                    .filter(tool -> tool.name().equals("addFirstTwoAndAllThree"))
                    .findFirst()
                    .orElseThrow();

            JsonNode inputSchema = addFirstTwoAndAllThree.inputSchema();
            assertThat(fieldNames(inputSchema.get("required"))).containsExactlyInAnyOrder("a", "b");
            assertThat(propertyNames(inputSchema.get("properties"))).containsExactlyInAnyOrder("a", "b", "c");

            JsonNode optionalParameterSchema = inputSchema.get("properties").get("c");
            assertThat(optionalParameterSchema.get("type").asText()).isEqualTo("integer");
            assertThat(optionalParameterSchema.has("properties")).isFalse();

            JsonNode outputSchema = addFirstTwoAndAllThree.outputSchema().orElseThrow();
            assertThat(outputSchema.get("type").asText()).isEqualTo("object");
            assertThat(fieldNames(outputSchema.get("required"))).containsExactlyInAnyOrder("firstTwo", "allThree");
            assertThat(propertyNames(outputSchema.get("properties"))).containsExactlyInAnyOrder("firstTwo", "allThree");
            assertThat(outputSchema.get("properties").get("firstTwo").get("type").asText()).isEqualTo("integer");
            assertThat(outputSchema.get("properties").get("allThree").get("type").asText()).isEqualTo("integer");

            CallToolRequest callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
            CallToolResult twoAndThreeCallToolResult = connection.callTool(callToolRequest);
            assertThat(twoAndThreeCallToolResult.isError()).isNotEqualTo(Optional.of(true));
            assertThat(requireStructuredContent(twoAndThreeCallToolResult, TwoAndThree.class)).isEqualTo(new TwoAndThree(3, 6));

            // Test not sending an optional parameter
            callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2));
            twoAndThreeCallToolResult = connection.callTool(callToolRequest);
            assertThat(twoAndThreeCallToolResult.isError()).isNotEqualTo(Optional.of(true));
            assertThat(requireStructuredContent(twoAndThreeCallToolResult, TwoAndThree.class)).isEqualTo(new TwoAndThree(3, 3));

            // Test the "error" path
            callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", -1, "b", -2, "c", -3));
            twoAndThreeCallToolResult = connection.callTool(callToolRequest);
            assertThat(twoAndThreeCallToolResult.isError()).isEqualTo(Optional.of(true));
            assertThat(twoAndThreeCallToolResult.structuredContent()).isEmpty();
            assertThat(requireContentString(twoAndThreeCallToolResult)).isEqualTo("Negative numbers are not allowed");
        }
    }

    @Test
    public void testExceptionWrapping()
    {
        try (var connection = createClient().connect(uri())) {
            CallToolResult callToolResult = connection.callTool(new CallToolRequest("throws", ImmutableMap.of()));
            assertThat(callToolResult.isError()).isEqualTo(Optional.of(true));
            assertThat(requireContentString(callToolResult)).isEqualTo("this ain't good");
        }
    }

    @Test
    public void testPrompts()
    {
        try (var connection = createClient().connect(uri())) {
            ListPromptsResult listPromptsResult = connection.listPrompts(Optional.empty());
            assertThat(listPromptsResult.prompts())
                    .extracting(Prompt::name)
                    .containsExactlyInAnyOrder("greeting", "age");

            GetPromptResult getPromptResult = connection.getPrompt(new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt")));
            assertThat(getPromptResult.messages().orElseThrow())
                    .hasSize(1)
                    .first()
                    .extracting(PromptMessage::content)
                    .asInstanceOf(type(TextContent.class))
                    .extracting(TextContent::text)
                    .isEqualTo("Hello, Galt!");
        }
    }

    @Test
    public void testTasks()
            throws InterruptedException
    {
        if (clientMode == LEGACY_PROTOCOL_ONLY) {
            return;
        }

        AtomicInteger handlerInvocations = new AtomicInteger();
        McpInputRequestsHandler handler = inputRequestMap -> {
            handlerInvocations.incrementAndGet();
            return ImmutableMap.of(inputRequestMap.keySet().iterator().next(), new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("confirm", true)), Optional.empty()));
        };

        try (McpTasksConnection connection = createTasksClient().withTasks().connect(uri())) {
            // a tool that completes immediately returns a CallToolResult - the processor passes it straight through
            ToolResult immediateResult = connection.callToolOrTask(new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2)));
            assertThat(immediateResult).isInstanceOf(CallToolResult.class);
            String result = handler.asTaskResultProcessor()
                    .process(connection, immediateResult, McpMapper::optionalContentString)
                    .required();
            assertThat(result).isEqualTo("3");
            assertThat(handlerInvocations).hasValue(0);

            // working -> completed
            ToolResult taskResult = connection.callToolOrTask(new CallToolRequest("task_add", ImmutableMap.of("a", 2, "b", 3)));
            assertThat(taskResult).isInstanceOf(Task.class);
            result = handler.asTaskResultProcessor()
                    .process(connection, taskResult, McpMapper::optionalContentString)
                    .required();
            assertThat(result).isEqualTo("5");
            assertThat(handlerInvocations).hasValue(0);

            // working -> input_required -> answered -> completed; the handler must be invoked exactly once
            taskResult = connection.callToolOrTask(new CallToolRequest("task_confirm", ImmutableMap.of("filename", "data.txt")));
            assertThat(taskResult).isInstanceOf(Task.class);
            result = handler.asTaskResultProcessor()
                    .process(connection, taskResult, McpMapper::optionalContentString)
                    .required();
            assertThat(result).isEqualTo("Deleted data.txt");
            assertThat(handlerInvocations).hasValue(1);

            // failed -> the server's error detail is thrown
            ToolResult failedResult = connection.callToolOrTask(new CallToolRequest("task_fail", ImmutableMap.of()));
            assertThat(failedResult).isInstanceOf(Task.class);
            assertThatThrownBy(() -> handler.asTaskResultProcessor().process(connection, failedResult, McpMapper::optionalContentString))
                    .satisfies(e -> assertMcpError(e, INTERNAL_ERROR.code(), "task failed as requested"));
        }
    }

    @Test
    public void testTooManyInputRequestRounds()
    {
        if (clientMode == LEGACY_PROTOCOL_ONLY) {
            // in legacy mode multi-round elicitation is emulated server-side, so the client-side
            // round guard does not apply
            return;
        }

        AtomicInteger handlerInvocations = new AtomicInteger();
        McpInputRequestsHandler handler = inputRequestMap -> {
            handlerInvocations.incrementAndGet();
            return ImmutableMap.of(inputRequestMap.keySet().iterator().next(), new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("confirm", true)), Optional.empty()));
        };

        // a tool that never converges - the round guard must stop the retry loop
        try (var connection = createClient().connect(uri())) {
            McpConnection limited = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);
            assertThatThrownBy(() -> handler.asResultProcessor().process(limited, new CallToolRequest("endless_input", ImmutableMap.of()), McpConnection::callTool, McpMapper::optionalContentString))
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("Too many input request rounds");
            assertThat(handlerInvocations).hasValue(3);
        }

        // the same guard for a task that asks for input forever
        handlerInvocations.set(0);
        try (McpTasksConnection connection = createTasksClient().withTasks().connect(uri())) {
            McpTasksConnection limited = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);
            ToolResult toolResult = limited.callToolOrTask(new CallToolRequest("task_endless_input", ImmutableMap.of()));
            assertThat(toolResult).isInstanceOf(Task.class);
            assertThatThrownBy(() -> handler.asTaskResultProcessor().process(limited, toolResult, McpMapper::optionalContentString))
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("Too many input request rounds");
            assertThat(handlerInvocations).hasValue(3);
        }
    }

    private McpClient createTasksClient()
    {
        // poll quickly so the test isn't paced by the 10 second default
        return createClient()
                .withSetting(MIN_TASK_SERVICE_PERIOD, Duration.ofMillis(50))
                .withSetting(MAX_TASK_SERVICE_PERIOD, Duration.ofMillis(200));
    }

    @Test
    public void testSubscriptionInterruption()
            throws Exception
    {
        if (clientMode == LEGACY_PROTOCOL_ONLY) {
            // the legacy listen stream is driven by HTTP GET, which is disabled for this server
            return;
        }

        BlockingQueue<String> notifications = new LinkedBlockingQueue<>();
        NotificationConsumer notificationConsumer = (_, method, _) -> notifications.add(method);

        try (McpConnection connection = createClient()
                .withDefaultConnectionSetting(NOTIFICATION_CONSUMER, notificationConsumer)
                .connect(uri())) {
            SubscriptionFilter filter = new SubscriptionFilter(Optional.of(true), Optional.of(true), Optional.of(true), Optional.empty());
            AutoCloseable subscription = connection.subscribe(new SubscriptionNotifications(filter, Optional.empty()));

            // the server acknowledges the subscription once the listen stream is live
            assertThat(awaitNotification(notifications, NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED)).isTrue();

            // positive control - while subscribed, a tools change is delivered
            connection.callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "tools")));
            assertThat(awaitNotification(notifications, NOTIFICATION_TOOLS_LIST_CHANGED)).isTrue();

            // interrupt the subscription - this closes the listen stream and stops its thread
            subscription.close();
            notifications.clear();

            // changes made after the interruption must not be delivered
            connection.callTool(new CallToolRequest("setVersion", ImmutableMap.of("type", "SYSTEM", "name", "prompts")));
            assertThat(notifications.poll(2, SECONDS)).isNull();
        }
    }

    private static boolean awaitNotification(BlockingQueue<String> notifications, String method)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            String value = notifications.poll(250, MILLISECONDS);
            if (method.equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testElicitation()
    {
        try (var connection = createClient().connect(uri())) {
            CallToolRequest callToolRequest = new CallToolRequest("elicitation", ImmutableMap.of());
            McpInputRequestsHandler handler = inputRequestMap -> ImmutableMap.of(inputRequestMap.keySet().iterator().next(), new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("firstName", "me", "lastName", "you")), Optional.empty()));
            String result = handler.asResultProcessor()
                    .process(connection, callToolRequest, McpConnection::callTool, McpMapper::optionalContentString)
                    .required();
            assertThat(result).isEqualTo("Hello, me you!");
        }
    }

    @Test
    public void testResources()
    {
        try (var connection = createClient().connect(uri())) {
            ListResourcesResult listResourcesResult = connection.listResources(Optional.empty());
            assertThat(listResourcesResult.resources())
                    .extracting(Resource::name)
                    .containsExactlyInAnyOrder("example1", "example2", "my-test-skill", "skill://index.json", "show-map");

            ReadResourceRequest readResourceRequest = new ReadResourceRequest("file://example2.txt");
            ReadResourceResult readResourceResult = connection.readResource(readResourceRequest);
            assertThat(readResourceResult.contents().orElseThrow())
                    .hasSize(1)
                    .first()
                    .extracting(ResourceContents::text)
                    .isEqualTo(Optional.of("This is the content of file://example2.txt"));

            readResourceRequest = new ReadResourceRequest("file://test.template");
            readResourceResult = connection.readResource(readResourceRequest);
            assertThat(readResourceResult.contents().orElseThrow())
                    .hasSize(1)
                    .first()
                    .extracting(ResourceContents::text)
                    .isEqualTo(Optional.of("ID is: test"));

            ReadResourceRequest badReadResourceRequest = new ReadResourceRequest("file://not-a-template");
            assertThatThrownBy(() -> connection.readResource(badReadResourceRequest))
                    .satisfies(e -> assertMcpError(e, INVALID_PARAMS.code(), "Resource not found: file://not-a-template"));
        }
    }

    @Test
    public void testSkills()
    {
        try (var connection = createClient().connect(uri())) {
            // the skills instructions are only added on the legacy path: the two initialize implementations use
            // McpMetadata.adjustedInstructions(), while OperationsImpl.serverDiscover() returns the unadjusted
            // McpMetadata.instructions(), so a current-protocol client never sees them
            if (clientMode == LEGACY_PROTOCOL_ONLY) {
                assertThat(connection.serverDiscover().instructions().orElseThrow()).contains(SKILLS_INSTRUCTIONS);
            }
            else {
                assertThat(connection.serverDiscover().instructions()).isEmpty();
            }

            ReadResourceResult readResourceResult = connection.readResource(new ReadResourceRequest(SKILL_INDEX_URI));
            assertThat(readResourceResult.contents().orElseThrow())
                    .hasSize(1)
                    .first()
                    .extracting(ResourceContents::text)
                    .isEqualTo(Optional.of("{\"$schema\":\"https://schemas.agentskills.io/discovery/0.2.0/schema.json\",\"skills\":[{\"name\":\"my-test-skill\",\"type\":\"skill-md\",\"url\":\"skill://a/b/c/my-test-skill/SKILL.md\",\"description\":\"An example skill.\"},{\"name\":\"my-test-skill-template\",\"type\":\"mcp-resource-template\",\"url\":\"skill://a/{name}/my-test-skill-template/SKILL.md\",\"description\":\"An example skill template.\"}]}"));
        }
    }

    @Test
    public void testMetaAnnotations()
    {
        try (var connection = createClient().connect(uri())) {
            Tool addTool = connection.listTools(Optional.empty()).tools().stream()
                    .filter(tool -> tool.name().equals("add"))
                    .findFirst()
                    .orElseThrow();
            Map<String, Object> meta = addTool.meta().orElseThrow();
            assertThat(meta).containsEntry("hey", ImmutableMap.of("x", 20.20));
            assertThat(meta).containsEntry("you", 12.34);
            assertThat(meta).containsEntry("there", "a");
            assertThat(meta).containsEntry("buddy", ImmutableList.of("a", "b", "c"));

            Prompt agePrompt = connection.listPrompts(Optional.empty()).prompts().stream()
                    .filter(prompt -> prompt.name().equals("age"))
                    .findFirst()
                    .orElseThrow();
            assertThat(agePrompt.meta().orElseThrow()).containsEntry("age", "12");

            Resource example1Resource = connection.listResources(Optional.empty()).resources().stream()
                    .filter(resource -> resource.name().equals("example1"))
                    .findFirst()
                    .orElseThrow();
            assertThat(example1Resource.meta().orElseThrow()).containsEntry("test", ImmutableList.of("1", "2"));

            ListResourceTemplatesResult listResourceTemplatesResult = connection.listResourceTemplates(Optional.empty());
            ResourceTemplate template = listResourceTemplatesResult.resourceTemplates().stream()
                    .filter(resourceTemplate -> resourceTemplate.name().equals("template"))
                    .findFirst()
                    .orElseThrow();
            assertThat(template.meta().orElseThrow()).containsEntry("test", "1");
        }
    }

    @Test
    public void testMcpApp()
    {
        try (var connection = createClient().connect(uri())) {
            Tool showMapTool = connection.listTools(Optional.empty()).tools().stream()
                    .filter(tool -> tool.name().equals("show-map"))
                    .findFirst()
                    .orElseThrow();
            assertThat(showMapTool.meta().orElseThrow()).containsEntry("ui", ImmutableMap.of("resourceUri", "ui://cesium-map/mcp-app.html"));

            ReadResourceResult readResourceResult = connection.readResource(new ReadResourceRequest("ui://cesium-map/mcp-app.html"));
            assertThat(readResourceResult.contents().orElseThrow()).isNotEmpty();
        }
    }

    @Test
    public void testExpiredSession()
    {
        if (clientMode != LEGACY_PROTOCOL_ONLY) {
            return;
        }

        StandardSessionController sessionController = sessionController();
        Set<SessionId> preSessionIds = sessionController.sessionIds();
        try (var connection = createClient().connect(uri())) {
            Set<SessionId> postSessionIds = sessionController.sessionIds();
            SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

            // simulate session expiring
            sessionController.deleteSession(clientSessionId);

            assertThatThrownBy(() -> connection.listTools(Optional.empty()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testDeleteSession()
    {
        if (clientMode != LEGACY_PROTOCOL_ONLY) {
            return;
        }

        StandardSessionController sessionController = sessionController();
        Set<SessionId> preSessionIds = sessionController.sessionIds();
        try (var connection = createClient().connect(uri())) {
            Set<SessionId> postSessionIds = sessionController.sessionIds();
            SessionId clientSessionId = Sets.difference(postSessionIds, preSessionIds).iterator().next();

            sessionController.deleteSession(clientSessionId);
            assertThat(sessionController.sessionIds()).doesNotContain(clientSessionId);

            assertThatThrownBy(() -> connection.listTools(Optional.empty()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private StandardSessionController sessionController()
    {
        return (StandardSessionController) injector().getInstance(Key.get(SessionController.class, ForSessionCaching.class));
    }

    private McpClient createClient()
    {
        return createClient(Optional.of(EXPECTED_IDENTITY));
    }

    private McpClient createClient(Optional<String> identity)
    {
        RequestFilter requestFilter = identity
                .map(value -> (RequestFilter) builder -> builder.setHeader(IDENTITY_HEADER, value))
                .orElseGet(() -> builder -> builder);

        return mcpClient(httpClient())
                .withSetting(ELICITATION_ENABLED, true)
                .withSetting(MODE, clientMode)
                .withDefaultConnectionSetting(REQUEST_FILTER, requestFilter);
    }

    private McpClient createClient(io.airlift.mcp.model.LoggingLevel loggingLevel, BlockingQueue<String> logs)
    {
        LoggingConsumer loggingConsumer = notification -> notification.data()
                .map(String::valueOf)
                .ifPresent(logs::add);

        return createClient()
                .withSetting(LOGGING_LEVEL, loggingLevel)
                .withDefaultConnectionSetting(NOTIFICATION_CONSUMER, loggingConsumer.asNotificationConsumer());
    }

    private McpClient createProgressClient(BlockingQueue<String> progress)
    {
        ProgressConsumer progressConsumer = notification -> progress.add(notification.message());

        return createClient().withDefaultConnectionSetting(NOTIFICATION_CONSUMER, progressConsumer.asNotificationConsumer());
    }

    private int rawPostStatus(Optional<String> identity)
    {
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(injector().getInstance(JsonMapper.class));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/list", ImmutableMap.of());

        Request.Builder builder = preparePost().setUri(uri())
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json, text/event-stream")
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest));
        identity.ifPresent(value -> builder.addHeader(IDENTITY_HEADER, value));

        return httpClient().execute(builder.build(), createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {}))).getStatusCode();
    }

    private static List<String> propertyNames(JsonNode node)
    {
        return ImmutableList.copyOf(node.fieldNames());
    }

    private static List<String> fieldNames(JsonNode node)
    {
        return ImmutableList.copyOf(node).stream()
                .map(JsonNode::asText)
                .collect(toImmutableList());
    }

    private static List<String> takeNFromQueue(BlockingQueue<String> queue, int qty)
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

    private static void assertMcpError(Throwable throwable, int code, String message)
    {
        assertThat(throwable)
                .asInstanceOf(type(McpException.class))
                .extracting(McpException::errorDetail)
                .extracting(JsonRpcErrorDetail::code, JsonRpcErrorDetail::message)
                .contains(code, message);
    }
}

/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Key;
import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.Request;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodec;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetPromptResult.PromptMessage;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.TestingIdentityMapper.ERRORED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcp
{
    public static final String IDENTITY_HEADER = "X-Testing-Identity";

    private final TestingServer testingServer;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final ObjectMapper objectMapper;
    private final List<String> lastRequestEvents = new ArrayList<>();

    public TestMcp()
    {
        testingServer = new TestingServer(ImmutableMap.of(), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .build());

        httpClient = testingServer.injector().getInstance(Key.get(HttpClient.class, ForTest.class));
        baseUri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
        objectMapper = testingServer.injector().getInstance(ObjectMapper.class);
    }

    @BeforeAll
    public void setup()
    {
        lastRequestEvents.clear();
    }

    @AfterAll
    public void shutdown()
    {
        testingServer.close();
    }

    static Stream<JsonRpcRequest<?>> authRpcRequests()
    {
        return Stream.of(
                JsonRpcRequest.buildRequest(1, "tools/list"),
                JsonRpcRequest.buildRequest(1, "tools/call", new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2))),
                JsonRpcRequest.buildRequest(1, "prompts/list", 1),
                JsonRpcRequest.buildRequest(1, "prompts/get", new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt"))),
                JsonRpcRequest.buildRequest(1, "resources/list"),
                JsonRpcRequest.buildRequest(1, "resources/read", new ReadResourceRequest("file://example1.txt")));
    }

    @ParameterizedTest
    @MethodSource("authRpcRequests")
    public void testAuth(JsonRpcRequest<?> request)
    {
        // No identity header
        JsonResponse<JsonRpcResponse<?>> response = rpcCall("", request);
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getHeaders("WWW-Authenticate")).hasSize(1).first().isEqualTo(IDENTITY_HEADER);
        assertThat(response.getResponseBody()).contains("Empty or missing identity header");

        // Invalid identity header
        response = rpcCall("Invalid Identity", request);
        assertThat(response.getStatusCode()).isEqualTo(403);
        assertThat(response.getHeaders("WWW-Authenticate")).isEmpty();
        assertThat(response.getResponseBody()).contains("Identity Invalid Identity is not authorized to access");

        response = rpcCall(ERRORED_IDENTITY, request);
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getHeaders("WWW-Authenticate")).isEmpty();
        assertThat(response.getResponseBody()).contains("This identity cannot catch a break");

        // Valid identity header
        response = rpcCall(EXPECTED_IDENTITY, request);
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    public void testInvalidRpcRequests()
    {
        CallToolRequest callToolRequest = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);

        // missing proper Accept header
        Request request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader(IDENTITY_HEADER, "Mr. Tester")
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        FullJsonResponseHandler.JsonResponse<Object> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .isEqualTo("{\"code\":%s,\"message\":\"Both application/json and text/event-stream required in Accept header\"}".formatted(INVALID_REQUEST.code()));

        // nonsensical object in body
        request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader(IDENTITY_HEADER, "Mr. Tester")
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<>() {}), new ListToolsResult(ImmutableList.of())))
                .build();
        response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .isEqualTo("{\"code\":%s,\"message\":\"Invalid message format\"}".formatted(INVALID_REQUEST.code()));
    }

    @Test
    public void testToolPrimitiveStructuredContent()
    {
        JsonRpcRequest<?> listToolsRpcRequest = JsonRpcRequest.buildRequest(1, "tools/list");

        JsonRpcResponse<?> listToolsResponse = rpcCall(listToolsRpcRequest);
        ListToolsResult listToolsResult = objectMapper.convertValue(listToolsResponse.result().orElseThrow(), ListToolsResult.class);
        assertThat(listToolsResult.tools())
                .filteredOn(tool -> tool.name().equals("addThree"))
                .hasSize(1)
                .first()
                .extracting(Tool::outputSchema)
                .satisfies(node -> assertThat(node.isEmpty()));

        CallToolRequest callToolRequest = new CallToolRequest("addThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(rpcRequest);
        CallToolResult callToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(callToolResult.structuredContent())
                .isEmpty();
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("6");
    }

    @Test
    public void testToolEmbeddedStructuredContent()
    {
        JsonRpcRequest<?> listToolsRpcRequest = JsonRpcRequest.buildRequest(1, "tools/list");

        JsonRpcResponse<?> listToolsResponse = rpcCall(listToolsRpcRequest);
        ListToolsResult listToolsResult = objectMapper.convertValue(listToolsResponse.result().orElseThrow(), ListToolsResult.class);
        assertThat(listToolsResult.tools())
                .filteredOn(tool -> tool.name().equals("addFirstTwoAndAllThree"))
                .hasSize(1)
                .first()
                .extracting(Tool::outputSchema)
                .extracting(Optional::get)
                .satisfies(node -> {
                    assertThat(node.get("type").asText()).isEqualTo("object");

                    assertThat(node.get("required"))
                            .isNotNull()
                            .extracting(JsonNode::asText)
                            .containsExactlyInAnyOrder("firstTwo", "allThree");

                    assertThat(node.get("properties")).isNotNull();
                    JsonNode properties = node.get("properties");
                    assertThat(properties.fieldNames()).toIterable().containsExactlyInAnyOrder("firstTwo", "allThree");

                    assertThat(properties.get("firstTwo").get("type").asText()).isEqualTo("integer");
                    assertThat(properties.get("allThree").get("type").asText()).isEqualTo("integer");
                });

        CallToolRequest callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2, "c", 3));
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(rpcRequest);
        CallToolResult twoAndThreeCallToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(twoAndThreeCallToolResult.isError()).isFalse();
        assertThat(twoAndThreeCallToolResult.structuredContent())
                .isPresent()
                .get()
                .extracting(StructuredContent::value)
                .isEqualTo(ImmutableMap.of("firstTwo", 3, "allThree", 6));

        // Test not sending an optional parameter
        callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", 1, "b", 2));
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        response = rpcCall(jsonrpcRequest);
        twoAndThreeCallToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(twoAndThreeCallToolResult.isError()).isFalse();
        assertThat(twoAndThreeCallToolResult.structuredContent())
                .isPresent()
                .get()
                .extracting(StructuredContent::value)
                .isEqualTo(ImmutableMap.of("firstTwo", 3, "allThree", 3));

        // Test the "error" path
        callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", -1, "b", -2, "c", -3));
        rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        response = rpcCall(rpcRequest);
        twoAndThreeCallToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(twoAndThreeCallToolResult.isError()).isTrue();
        assertThat(twoAndThreeCallToolResult.structuredContent()).isEmpty();
        assertThat(twoAndThreeCallToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("Negative numbers are not allowed");
    }

    @Test
    public void testTools()
    {
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/list");

        JsonRpcResponse<?> response = rpcCall(rpcRequest);
        ListToolsResult listToolsResult = objectMapper.convertValue(response.result().orElseThrow(), ListToolsResult.class);
        assertThat(listToolsResult.tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("add", "throws", "addThree", "addFirstTwoAndAllThree", "progress", "log", "elicitationThenSample", "testElicitation", "taskThatThrows");

        CallToolRequest callToolRequest = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        response = rpcCall(rpcRequest);
        CallToolResult callToolResult = objectMapper.convertValue(response.result().orElseThrow(), CallToolResult.class);
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("3");
    }

    @Test
    public void testExceptionWrapping()
    {
        CallToolRequest callToolRequest = new CallToolRequest("throws", ImmutableMap.of());
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(rpcRequest);

        assertThat(response.error())
                .contains(new JsonRpcErrorDetail(INVALID_REQUEST, "this ain't good"));
    }

    @Test
    public void testPrompts()
    {
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "prompts/list", 1);

        JsonRpcResponse<?> response = rpcCall(rpcRequest);
        ListPromptsResult listPromptsResult = objectMapper.convertValue(response.result().orElseThrow(), ListPromptsResult.class);
        assertThat(listPromptsResult.prompts())
                .extracting(Prompt::name)
                .containsExactlyInAnyOrder("greeting");

        GetPromptRequest getPromptRequest = new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt"));
        rpcRequest = JsonRpcRequest.buildRequest(1, "prompts/get", getPromptRequest);
        response = rpcCall(rpcRequest);
        GetPromptResult getPromptResult = objectMapper.convertValue(response.result().orElseThrow(), GetPromptResult.class);
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
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "resources/list");

        JsonRpcResponse<?> response = rpcCall(rpcRequest);
        ListResourcesResult listResourcesResult = objectMapper.convertValue(response.result().orElseThrow(), ListResourcesResult.class);
        assertThat(listResourcesResult.resources())
                .extracting(Resource::name)
                .containsExactlyInAnyOrder("example1", "example2");

        ReadResourceRequest readResourceRequest = new ReadResourceRequest("file://example2.txt");
        rpcRequest = JsonRpcRequest.buildRequest(1, "resources/read", readResourceRequest);
        response = rpcCall(rpcRequest);
        ReadResourceResult readResourceResult = objectMapper.convertValue(response.result().orElseThrow(), ReadResourceResult.class);
        assertThat(readResourceResult.contents())
                .hasSize(1)
                .first()
                .extracting(ResourceContents::text)
                .isEqualTo(Optional.of("This is the content of file://example2.txt"));

        readResourceRequest = new ReadResourceRequest("file://test.template");
        rpcRequest = JsonRpcRequest.buildRequest(1, "resources/read", readResourceRequest);
        response = rpcCall(rpcRequest);
        readResourceResult = objectMapper.convertValue(response.result().orElseThrow(), ReadResourceResult.class);
        assertThat(readResourceResult.contents())
                .hasSize(1)
                .first()
                .extracting(ResourceContents::text)
                .isEqualTo(Optional.of("ID is: test"));

        readResourceRequest = new ReadResourceRequest("file://not-a-template");
        rpcRequest = JsonRpcRequest.buildRequest(1, "resources/read", readResourceRequest);
        response = rpcCall(rpcRequest);
        assertThat(response.error()).map(JsonRpcErrorDetail::code).contains(-32002);
    }

    @Test
    public void testGetMcpReturns405()
    {
        Request request = prepareGet()
                .setUri(baseUri)
                .addHeader("Accept", "application/json,text/event-stream")
                .build();

        JsonResponse<Object> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_UNAUTHORIZED);

        request = prepareGet()
                .setUri(baseUri)
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .build();

        response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void testProgress()
            throws JsonProcessingException
    {
        CallToolRequest callToolRequest = new CallToolRequest("progress", ImmutableMap.of());
        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCallSse("Mr. Tester", rpcRequest).getValue();

        for (int i = 0; i <= 100; ++i) {
            ProgressNotification progressNotification = new ProgressNotification(Optional.empty(), "Progress " + i + "%", OptionalDouble.of(i), OptionalDouble.of(100));
            String expectedProgressEvent = objectMapper.writeValueAsString(JsonRpcRequest.buildNotification("notifications/progress", progressNotification));
            assertThat(lastRequestEvents).hasSizeGreaterThanOrEqualTo(i);
            String event = lastRequestEvents.get(i);
            assertThat(event).isEqualTo(expectedProgressEvent);
        }

        CallToolResult callToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(TextContent.class))
                .extracting(TextContent::text)
                .isEqualTo("true");
    }

    private JsonRpcResponse<?> rpcCall(JsonRpcRequest<?> rpcRequest)
    {
        return rpcCall("Mr. Tester", rpcRequest).getValue();
    }

    private JsonResponse<JsonRpcResponse<?>> rpcCall(String identityHeader, JsonRpcRequest<?> rpcRequest)
    {
        Request request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, identityHeader)
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        return httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
    }

    private JsonResponse<JsonRpcResponse<?>> rpcCallSse(String identityHeader, JsonRpcRequest<?> rpcRequest)
    {
        JsonCodec<JsonRpcResponse<?>> responseCodec = jsonCodec(new TypeToken<>() {});

        Request request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, identityHeader)
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        lastRequestEvents.clear();

        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            if (response.getStatusCode() > 299) {
                return new JsonResponse<>(response.getStatusCode(), response.getHeaders(), responseCodec, response.getInputStream().readAllBytes());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream()));

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                if (line.startsWith("data: ")) {
                    lastRequestEvents.add(line.substring("data: ".length()));
                }
            }

            if (lastRequestEvents.isEmpty()) {
                throw new UncheckedIOException(new IOException("No events received from MCP server"));
            }

            return new JsonResponse<>(response.getStatusCode(), response.getHeaders(), responseCodec, lastRequestEvents.getLast().getBytes(UTF_8));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

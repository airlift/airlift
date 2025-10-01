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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
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
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;
import io.airlift.node.NodeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcp
{
    private static final String IDENTITY_HEADER = "X-Testing-Identity";

    private final HttpClient httpClient;
    private final Injector injector;
    private final URI baseUri;
    private final ObjectMapper objectMapper;

    public TestMcp()
    {
        Module mcpModule = McpModule.builder()
                .withAllInClass(TestingEndpoints.class)
                .withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance(request -> new TestingIdentity(request.getHeader(IDENTITY_HEADER))))
                .build();

        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(mcpModule)
                .add(binder -> httpClientBinder(binder).bindHttpClient("test", ForTest.class))
                .add(new NodeModule())
                .add(new TestingHttpServerModule())
                .add(new JaxrsModule())
                .add(new JsonModule());

        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "testing");

        Bootstrap app = new Bootstrap(modules.build());
        injector = app.setRequiredConfigurationProperties(serverProperties.build()).initialize();

        httpClient = injector.getInstance(Key.get(HttpClient.class, ForTest.class));
        baseUri = injector.getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    @AfterAll
    public void shutdown()
    {
        injector.getInstance(LifeCycleManager.class).stop();
    }

    @Test
    public void testInvalidRpcRequests()
    {
        CallToolRequest callToolRequest = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);

        // missing proper Accept header
        Request request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader(IDENTITY_HEADER, "Mr. Tester")
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), jsonrpcRequest))
                .build();

        FullJsonResponseHandler.JsonResponse<Object> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getResponseBody())
                .isEqualTo("{\"message\":\"Both application/json and text/event-stream required in Accept header\"}");

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
                .isEqualTo("{\"message\":\"Invalid message format\"}");
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
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);
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
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);
        CallToolResult twoAndThreeCallToolResult = objectMapper.convertValue(response.result().orElseThrow(), new TypeReference<>() {});
        assertThat(twoAndThreeCallToolResult.isError()).isFalse();
        assertThat(twoAndThreeCallToolResult.structuredContent())
                .isPresent()
                .get()
                .extracting(StructuredContent::value)
                .isEqualTo(ImmutableMap.of("firstTwo", 3, "allThree", 6));

        // Test the "error" path
        callToolRequest = new CallToolRequest("addFirstTwoAndAllThree", ImmutableMap.of("a", -1, "b", -2, "c", -3));
        jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        response = rpcCall(jsonrpcRequest);
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
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/list");

        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);
        ListToolsResult listToolsResult = objectMapper.convertValue(response.result().orElseThrow(), ListToolsResult.class);
        assertThat(listToolsResult.tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("add", "throws", "addThree", "addFirstTwoAndAllThree");

        CallToolRequest callToolRequest = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));
        jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        response = rpcCall(jsonrpcRequest);
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
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "tools/call", callToolRequest);
        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);

        assertThat(response.error())
                .contains(new JsonRpcErrorDetail(INVALID_REQUEST, "this ain't good"));
    }

    @Test
    public void testPrompts()
    {
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "prompts/list", 1);

        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);
        ListPromptsResult listPromptsResult = objectMapper.convertValue(response.result().orElseThrow(), ListPromptsResult.class);
        assertThat(listPromptsResult.prompts())
                .extracting(Prompt::name)
                .containsExactlyInAnyOrder("greeting");

        GetPromptRequest getPromptRequest = new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt"));
        jsonrpcRequest = JsonRpcRequest.buildRequest(1, "prompts/get", getPromptRequest);
        response = rpcCall(jsonrpcRequest);
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
        JsonRpcRequest<?> jsonrpcRequest = JsonRpcRequest.buildRequest(1, "resources/list");

        JsonRpcResponse<?> response = rpcCall(jsonrpcRequest);
        ListResourcesResult listResourcesResult = objectMapper.convertValue(response.result().orElseThrow(), ListResourcesResult.class);
        assertThat(listResourcesResult.resources())
                .extracting(Resource::name)
                .containsExactlyInAnyOrder("example1", "example2");

        ReadResourceRequest readResourceRequest = new ReadResourceRequest("file://example2.txt");
        jsonrpcRequest = JsonRpcRequest.buildRequest(1, "resources/read", readResourceRequest);
        response = rpcCall(jsonrpcRequest);
        ReadResourceResult readResourceResult = objectMapper.convertValue(response.result().orElseThrow(), ReadResourceResult.class);
        assertThat(readResourceResult.contents())
                .hasSize(1)
                .first()
                .extracting(ResourceContents::text)
                .isEqualTo(Optional.of("This is the content of file://example2.txt"));
    }

    private JsonRpcResponse<?> rpcCall(JsonRpcRequest<?> jsonrpcRequest)
    {
        Request request = preparePost().setUri(baseUri)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader(IDENTITY_HEADER, "Mr. Tester")
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), jsonrpcRequest))
                .build();

        return httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
    }
}

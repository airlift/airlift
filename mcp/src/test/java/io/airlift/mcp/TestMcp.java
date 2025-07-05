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
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import io.airlift.TestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StreamingResponse;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.CompletionResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResponse;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.model.Constants.METHOD_CALL_TOOL;
import static io.airlift.mcp.model.Constants.METHOD_GET_PROMPT;
import static io.airlift.mcp.model.Constants.METHOD_READ_RESOURCES;
import static io.airlift.mcp.model.Constants.PROTOCOL_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class TestMcp
        extends TestBase
{
    public TestMcp()
    {
        super(McpModule.builder().addAllInClass(TestingEndpoints.class));
    }

    @Test
    public void testTools()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        Request request = buildRequest(requestId, "tools/list", new TypeToken<>() {}, Optional.empty());
        JsonRpcResponse<ListToolsResponse> toolsResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(toolsResponse.result()).map(ListToolsResponse::tools).get()
                .asInstanceOf(InstanceOfAssertFactories.list(Tool.class))
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("add", "uppercase", "lookupPerson", "throws", "uppercaseSoon", "itsSimple");

        request = buildRequest(requestId, METHOD_CALL_TOOL, new TypeToken<>() {}, Optional.of(new CallToolRequest("add", ImmutableMap.of("x", 6, "y", 24))));
        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            for (Map<String, String> event : readEvents(response.getInputStream())) {
                JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                CallToolResult callToolResult = jsonRpcResponse.result().map(o -> objectMapper.convertValue(o, CallToolResult.class)).orElseThrow();

                assertThat(callToolResult.content())
                        .hasSize(1)
                        .first()
                        .asInstanceOf(InstanceOfAssertFactories.type(TextContent.class))
                        .extracting(TextContent::text)
                        .isEqualTo("30");

                break;
            }
        }
    }

    @Test
    public void testPrompts()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        Request request = buildRequest(requestId, "prompts/list", new TypeToken<>() {}, Optional.empty());
        JsonRpcResponse<ListPromptsResult> promptsResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        assertThat(promptsResponse.result()).map(ListPromptsResult::prompts).get()
                .asInstanceOf(InstanceOfAssertFactories.list(Prompt.class))
                .extracting(Prompt::name)
                .containsExactlyInAnyOrder("greeting", "progress");

        request = buildRequest(requestId, METHOD_GET_PROMPT, new TypeToken<>() {}, Optional.of(new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt"))));
        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            for (Map<String, String> event : readEvents(response.getInputStream())) {
                JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                GetPromptResult getPromptResult = jsonRpcResponse.result().map(o -> objectMapper.convertValue(o, GetPromptResult.class)).orElseThrow();

                assertThat(getPromptResult.messages())
                        .hasSize(1)
                        .first()
                        .extracting(GetPromptResult.PromptMessage::content)
                        .asInstanceOf(InstanceOfAssertFactories.type(TextContent.class))
                        .extracting(TextContent::text)
                        .isEqualTo("Hello, Galt!");

                break;
            }
        }
    }

    @Test
    public void testResources()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        Request request = buildRequest(requestId, "resources/list", new TypeToken<>() {}, Optional.empty());
        JsonRpcResponse<ListResourcesResult> resourcesResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        ListResourcesResult listResourcesResult = resourcesResponse.result().orElseThrow();
        assertThat(listResourcesResult.resources())
                .extracting(Resource::name)
                .containsExactlyInAnyOrder("example1", "example2");

        request = buildRequest(requestId, METHOD_READ_RESOURCES, new TypeToken<>() {}, Optional.of(new ReadResourceRequest("file://example1.txt", Optional.empty())));
        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            for (Map<String, String> event : readEvents(response.getInputStream())) {
                JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                ReadResourceResult readResourceResult = jsonRpcResponse.result().map(o -> objectMapper.convertValue(o, ReadResourceResult.class)).orElseThrow();

                assertThat(readResourceResult.contents())
                        .hasSize(1)
                        .first()
                        .extracting(ResourceContents::text)
                        .extracting(Optional::orElseThrow)
                        .isEqualTo("This is the content of file://example1.txt");

                break;
            }
        }
    }

    @Test
    public void testResourceTemplates()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        Request request = buildRequest(requestId, "resources/templates/list", new TypeToken<>() {}, Optional.empty());
        JsonRpcResponse<ListResourceTemplatesResult> resourcesResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        ListResourceTemplatesResult templatesResult = resourcesResponse.result().orElseThrow();
        assertThat(templatesResult.resourceTemplates())
                .hasSize(1)
                .extracting(ResourceTemplate::uriTemplate)
                .containsExactly("file://{part}.txt");

        request = buildRequest(requestId, METHOD_READ_RESOURCES, new TypeToken<>() {}, Optional.of(new ReadResourceRequest("file://one.txt", Optional.empty())));
        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            for (Map<String, String> event : readEvents(response.getInputStream())) {
                JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                ReadResourceResult resourceResult = jsonRpcResponse.result().map(o -> objectMapper.convertValue(o, ReadResourceResult.class)).orElseThrow();

                assertThat(resourceResult.contents())
                        .hasSize(1)
                        .extracting(ResourceContents::text)
                        .extracting(Optional::orElseThrow)
                        .containsExactly("one");

                break;
            }
        }
    }

    @Test
    public void testCompletions()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        CompletionRequest completionRequest = new CompletionRequest(new CompletionReference.Prompt("greeting"), new CompletionRequest.Argument("name", "hey"), Optional.empty());
        Request request = buildRequest(requestId, "completion/complete", new TypeToken<>() {}, Optional.of(completionRequest));
        try (StreamingResponse response = httpClient.executeStreaming(request)) {
            for (Map<String, String> event : readEvents(response.getInputStream())) {
                JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                CompletionResult completionResult = jsonRpcResponse.result().map(o -> objectMapper.convertValue(o, CompletionResult.class)).orElseThrow();

                assertThat(completionResult.completion().values())
                        .containsExactlyInAnyOrder("yo", "hey");

                break;
            }
        }
    }

    private void initialize(String requestId)
    {
        InitializeRequest initializeRequest = new InitializeRequest(PROTOCOL_VERSION, new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.empty()), new Implementation("hey", "1.0.0"));
        Request request = buildRequest(requestId, "initialize", new TypeToken<>() {}, initializeRequest);
        httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));

        request = buildRequest(requestId, "notifications/initialized", new TypeToken<>() {}, Optional.empty());
        StatusResponse status = httpClient.execute(request, createStatusResponseHandler());
        assertThat(status.getStatusCode()).isBetween(200, 299);
    }

    private <T> T parseData(Map<String, String> event, TypeReference<T> type)
    {
        String data = event.get("data");
        if (data == null) {
            return fail("No data in event: " + event);
        }
        try {
            return objectMapper.readValue(data, type);
        }
        catch (Exception e) {
            return fail(e);
        }
    }
}

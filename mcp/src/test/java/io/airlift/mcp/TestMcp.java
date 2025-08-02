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
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StreamingResponse;
import io.airlift.jaxrs.JaxrsBinder;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CancellationRequest;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.CompletionResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.ListPromptsRequest;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesRequest;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsRequest;
import io.airlift.mcp.model.ListToolsResponse;
import io.airlift.mcp.model.Paginated;
import io.airlift.mcp.model.PaginationMetadata;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.session.SessionMetadata;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.model.Constants.METHOD_CALL_TOOL;
import static io.airlift.mcp.model.Constants.METHOD_GET_PROMPT;
import static io.airlift.mcp.model.Constants.METHOD_READ_RESOURCES;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.SESSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcp
        extends TestBase
{
    public TestMcp()
    {
        super(moduleBuilder(), binder -> {
            binder.bind(TestingSessionController.class).in(SINGLETON);

            JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
            jaxrsBinder.bind(TestingValueParam.class);
            jaxrsBinder.bind(TestingContextResolver.class);
        });
    }

    private static McpModule.Builder moduleBuilder()
    {
        return McpModule.builder()
                .addAllInClass(TestingEndpoints.class)
                .withSessionHandling(SessionMetadata.DEFAULT, binding -> binding.to(TestingSessionController.class).in(SINGLETON))
                .withPaginationMetadata(new PaginationMetadata(2));
    }

    @Test
    public void testTools()
            throws IOException
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        List<Tool> tools = listAllPaginated(ListToolsResponse::tools, cursor -> {
            Request request = buildRequest(requestId, "tools/list", new TypeToken<>() {}, Optional.of(new ListToolsRequest(cursor)));
            JsonRpcResponse<ListToolsResponse> toolsResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
            return toolsResponse.result().orElseThrow();
        });
        assertThat(tools)
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("add", "uppercase", "lookupPerson", "throws", "uppercaseSoon", "itsSimple", "logging", "sendResourcesUpdatedNotification", "sendListChangedEvents", "showCurrentRoots", "sendSamplingMessage", "takeCreateMessageResults");

        Request request = buildRequest(requestId, METHOD_CALL_TOOL, new TypeToken<>() {}, Optional.of(new CallToolRequest("add", ImmutableMap.of("x", 6, "y", 24))));
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

        List<Prompt> prompts = listAllPaginated(ListPromptsResult::prompts, cursor -> {
            Request request = buildRequest(requestId, "prompts/list", new TypeToken<>() {}, Optional.of(new ListPromptsRequest(cursor)));
            JsonRpcResponse<ListPromptsResult> promptsResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
            return promptsResponse.result().orElseThrow();
        });
        assertThat(prompts)
                .extracting(Prompt::name)
                .containsExactlyInAnyOrder("greeting", "progress", "testCancellation");

        Request request = buildRequest(requestId, METHOD_GET_PROMPT, new TypeToken<>() {}, Optional.of(new GetPromptRequest("greeting", ImmutableMap.of("name", "Galt"))));
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

        List<Resource> resources = listAllPaginated(ListResourcesResult::resources, cursor -> {
            Request request = buildRequest(requestId, "resources/list", new TypeToken<>() {}, Optional.of(new ListResourcesRequest(cursor)));
            JsonRpcResponse<ListResourcesResult> resourcesResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
            return resourcesResponse.result().orElseThrow();
        });
        assertThat(resources)
                .extracting(Resource::name)
                .containsExactlyInAnyOrder("example1", "example2");

        Request request = buildRequest(requestId, METHOD_READ_RESOURCES, new TypeToken<>() {}, Optional.of(new ReadResourceRequest("file://example1.txt", Optional.empty())));
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

        List<ResourceTemplate> resourceTemplates = listAllPaginated(ListResourceTemplatesResult::resourceTemplates, cursor -> {
            Request request = buildRequest(requestId, "resources/templates/list", new TypeToken<>() {}, Optional.of(new ListResourceTemplatesRequest(cursor)));
            JsonRpcResponse<ListResourceTemplatesResult> resourcesResponse = httpClient.execute(request, createJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
            return resourcesResponse.result().orElseThrow();
        });
        assertThat(resourceTemplates)
                .hasSize(1)
                .extracting(ResourceTemplate::uriTemplate)
                .containsExactly("file://{part}.txt");

        Request request = buildRequest(requestId, METHOD_READ_RESOURCES, new TypeToken<>() {}, Optional.of(new ReadResourceRequest("file://one.txt", Optional.empty())));
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

    @Test
    public void testCancellation()
            throws Exception
    {
        String requestId = UUID.randomUUID().toString();
        initialize(requestId);

        CountDownLatch startedLatch = new CountDownLatch(1);
        Future<Boolean> future = Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            Request request = buildRequest(requestId, METHOD_GET_PROMPT, new TypeToken<>() {}, Optional.of(new GetPromptRequest("testCancellation", ImmutableMap.of())));
            try (StreamingResponse response = httpClient.executeStreaming(request)) {
                startedLatch.countDown();

                for (Map<String, String> event : readEvents(response.getInputStream())) {
                    JsonRpcResponse<Object> jsonRpcResponse = parseData(event, new TypeReference<>() {});
                    if (jsonRpcResponse.error().isPresent()) {
                        throw new RuntimeException("Error in response: " + jsonRpcResponse.error().get());
                    }
                }

                return true;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertTrue(startedLatch.await(10, TimeUnit.SECONDS));

        Request request = buildNotification(NOTIFICATION_CANCELLED, new TypeToken<>() {}, new CancellationRequest(requestId));
        StatusResponse status = httpClient.execute(request, createStatusResponseHandler());
        assertThat(status.getStatusCode()).isBetween(200, 299);

        assertTrue(future.get(10, TimeUnit.SECONDS));
    }

    private void initialize(String requestId)
    {
        InitializeRequest initializeRequest = new InitializeRequest(PROTOCOL_VERSION, new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.empty()), new Implementation("hey", "1.0.0"));
        Request request = buildRequest(requestId, "initialize", new TypeToken<>() {}, initializeRequest);
        JsonResponse<Object> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(new TypeToken<>() {})));
        requestHeaders.put(SESSION_HEADER, response.getHeader(SESSION_HEADER));

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

    private <R extends Paginated, T> List<T> listAllPaginated(Function<R, List<T>> mapper, Function<Optional<String>, R> requestProc)
    {
        Optional<String> cursor = Optional.empty();

        List<T> results = new ArrayList<>();
        do {
            R response = requestProc.apply(cursor);
            List<T> items = mapper.apply(response);
            results.addAll(items);
            cursor = response.nextCursor();
        }
        while (cursor.isPresent());

        return results;
    }
}

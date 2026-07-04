package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteResult.CompleteCompletion;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.CreateMessageRequest;
import io.airlift.mcp.model.CreateMessageResult;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetPromptResult.PromptMessage;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InitializeRequest.Sampling;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.Tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.clientCapabilityError;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_LOG_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;
import static io.airlift.mcp.model.Constants.METHOD_SAMPLING_CREATE_MESSAGE;
import static io.airlift.mcp.model.LoggingLevel.INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

// see: https://github.com/modelcontextprotocol/conformance/blob/main/examples/servers/typescript/everything-server.ts
public class ConformanceEndpoints
{
    private static final String TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
    private static final String TEST_AUDIO_BASE64 = "UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAAB9AAACABAAZGF0YQIAAAA=";

    private final JsonMapper jsonMapper;
    private final McpEntities entities;

    @Inject
    public ConformanceEndpoints(JsonMapper jsonMapper, McpEntities entities)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.entities = requireNonNull(entities, "entities is null");
    }

    @McpTool(name = "test_simple_text", description = "Tests simple text content response")
    public String testSimpleText()
    {
        return "This is a simple text response for testing.";
    }

    @McpTool(name = "test_image_content", description = "Tests image content response")
    public Content testImageContent()
    {
        return new ImageContent(TEST_IMAGE_BASE64, "image/png");
    }

    @McpTool(name = "test_audio_content", description = "Tests audio content response")
    public Content testAudioContent()
    {
        return new AudioContent(TEST_AUDIO_BASE64, "audio/wav");
    }

    @McpTool(name = "test_embedded_resource", description = "Tests embedded resource content response")
    public Content testEmbeddedResource()
    {
        return new EmbeddedResource(new ResourceContents(Optional.empty(), "test://embedded-resource", "text/plain", Optional.of("This is an embedded resource content."), Optional.empty()), Optional.empty());
    }

    @McpTool(name = "test_multiple_content_types", description = "Tests response with multiple content types (text, image, resource)")
    public CallToolResult testMultipleContentTypes()
    {
        List<Content> content = ImmutableList.of(
                new TextContent("Multiple content types test:"),
                new ImageContent(TEST_IMAGE_BASE64, "image/png"),
                new EmbeddedResource(new ResourceContents(Optional.empty(), "test://mixed-content-resource", "application/json", Optional.of("{ test: 'data', value: 123 }"), Optional.empty()), Optional.empty()));
        return new CallToolResult(content);
    }

    @McpTool(name = "test_tool_with_logging", description = "Tests tool that emits log messages during execution")
    public String testToolWithLogging(McpRequestContext requestContext)
    {
        requestContext.sendLog(INFO, "Tool execution started");
        requestContext.sendLog(INFO, "Tool processing data");
        requestContext.sendLog(INFO, "Tool execution completed");

        return "Tool with logging executed successfully";
    }

    @McpTool(name = "test_tool_with_progress", description = "Tests tool that reports progress notifications")
    public String testToolWithProgress(McpRequestContext requestContext, CallToolRequest request)
    {
        requestContext.sendProgress(0, 100, "Completed step 0 of 100");
        requestContext.sendProgress(50, 100, "Completed step 50 of 100");
        requestContext.sendProgress(100, 100, "Completed step 100 of 100");

        return request.meta().map(meta -> Optional.ofNullable(meta.get("progressToken")).orElse("?"))
                .orElse("??")
                .toString();
    }

    @McpTool(name = "test_error_handling", description = "Tests error response handling")
    public void testErrorHandling()
    {
        throw exception("This tool intentionally returns an error for testing");
    }

    @McpTool(name = "test_sampling", description = "Tests server-initiated sampling (LLM completion request)")
    public CallToolResult testSampling(@McpDescription("The prompt to send to the LLM") String prompt, InputResponses inputResponses)
    {
        return inputResponses.getInputResponse("test")
                .map(value -> jsonMapper.convertValue(value, CreateMessageResult.class))
                .map(messageResult -> {
                    String responseText = (messageResult.content() instanceof TextContent textContent) ? textContent.text() : "No response";
                    return new CallToolResult(new TextContent("LLM response: " + responseText));
                })
                .orElseGet(() -> {
                    CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent(prompt), 100);
                    return CallToolResult.inputRequestsBuilder()
                            .add("test", METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest)
                            .build();
                });
    }

    public record TestElicitation(String response) {}

    @McpTool(name = "test_elicitation", description = "Tests server-initiated elicitation (user input request)")
    public CallToolResult testElicitation(@McpDescription("The message to show the user") String message, InputResponses inputResponses)
    {
        return inputResponses.getInputResponse("test")
                .map(value -> jsonMapper.convertValue(value, ElicitResult.class))
                .map(result -> new CallToolResult(new TextContent("User response: action=%s, content=%s".formatted(result.action(), mapToJson(result.content())))))
                .orElseGet(() -> {
                    ObjectNode schema = new JsonSchemaBuilder().build(Optional.of("User's response"), TestElicitation.class);
                    ElicitRequestForm elicitRequestForm = new ElicitRequestForm(message, schema);
                    return CallToolResult.inputRequestsBuilder()
                            .add("test", METHOD_ELICITATION_CREATE, elicitRequestForm)
                            .build();
                });
    }

    @McpTool(name = "test_missing_capability", description = "Test tool requiring sampling")
    public String testMissingCapability(McpRequestContext requestContext)
    {
        if (requestContext.clientCapabilities().sampling().isEmpty()) {
            throw clientCapabilityError(new ClientCapabilities(Optional.empty(), Optional.of(new Sampling()), Optional.empty(), Optional.empty(), Optional.empty()));
        }
        return "Success";
    }

    @McpTool(name = "test_streaming_elicitation", description = "Diagnostic tool validating response progress streams")
    public String testStreamingElicitation(McpRequestContext requestContext)
    {
        requestContext.sendProgress(50, 100, "Starting elicitation");
        return "Streaming complete";
    }

    @McpTool(name = "test_logging_tool", description = "Diagnostic logging validator tool")
    public String testLoggingTool(McpRequestContext requestContext, CallToolRequest request)
    {
        if (request.meta().isPresent() && request.meta().orElseThrow().containsKey(METADATA_CLIENT_LOG_LEVEL)) {
            requestContext.sendLog(INFO, "Diagnostic trace logging activated");
        }
        return "Logging evaluated";
    }

    private String mapToJson(Optional<Map<String, Object>> map)
    {
        try {
            return jsonMapper.writeValueAsString(map.orElseGet(ImmutableMap::of));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @McpPromptCompletion(name = "test_prompt_with_arguments")
    public CompleteCompletion completion()
    {
        return new CompleteCompletion(ImmutableList.of("paris", "park", "party"), OptionalInt.of(150), OptionalBoolean.FALSE);
    }

    @McpResource(name = "static-text", uri = "test://static-text", mimeType = "text/plain", description = "A static text resource for testing")
    public ResourceContents staticText()
    {
        return new ResourceContents("static-text", "test://static-text", "text/plain", "This is the content of the static text resource.");
    }

    @McpResource(name = "static-binary", uri = "test://static-binary", mimeType = "image/png", description = "A static binary resource (image) for testing")
    public ResourceContents staticBinary()
    {
        return new ResourceContents(Optional.of("static-binary"), "test://static-binary", "image/png", Optional.empty(), Optional.of(TEST_IMAGE_BASE64));
    }

    @McpResourceTemplate(name = "template", uriTemplate = "test://template/{id}/data", mimeType = "application/json", description = "A resource template with parameter substitution")
    public ResourceContents resourceTemplate(ReadResourceRequest readResourceRequest, ResourceTemplateValues values)
            throws JsonProcessingException
    {
        String id = values.templateValues().get("id");
        String json = jsonMapper.writeValueAsString(ImmutableMap.of("id", id, "templateTest", true, "data", "Data for ID: " + id));
        return new ResourceContents("template", readResourceRequest.uri(), "application/json", json);
    }

    @McpResource(name = "watched-resource", uri = "test://watched-resource", mimeType = "text/plain", description = "A resource that auto-updates every 3 seconds")
    public ResourceContents watchedResource()
    {
        return new ResourceContents("watched-resource", "test://watched-resource", "text/plain", "Watched resource content");
    }

    @McpPrompt(name = "test_simple_prompt", description = "A simple prompt without arguments")
    public String testSimplePrompt()
    {
        return "This is a simple prompt for testing.";
    }

    @McpPrompt(name = "test_prompt_with_arguments", description = "A prompt with required arguments")
    public String testPromptWithArguments(@McpDescription("First test argument") String arg1, @McpDescription("Second test argument") String arg2)
    {
        return "Prompt with arguments: arg1='%s', arg2='%s'".formatted(arg1, arg2);
    }

    @McpPrompt(name = "test_prompt_with_embedded_resource", description = "A prompt that includes an embedded resource")
    public GetPromptResult testPromptWithEmbeddedResource(String resourceUri)
    {
        List<PromptMessage> messages = ImmutableList.of(
                new PromptMessage(Role.USER, new EmbeddedResource(new ResourceContents(Optional.empty(), resourceUri, "text/plain", Optional.of("Embedded resource content for testing."), Optional.empty()), Optional.empty())),
                new PromptMessage(Role.USER, new TextContent("Please process the embedded resource above.")));
        return new GetPromptResult(Optional.empty(), messages);
    }

    @McpPrompt(name = "test_prompt_with_image", description = "A prompt that includes image content")
    public GetPromptResult testPromptWithImage()
    {
        List<PromptMessage> messages = ImmutableList.of(
                new PromptMessage(Role.USER, new ImageContent(TEST_IMAGE_BASE64, "image/png")),
                new PromptMessage(Role.USER, new TextContent("Please analyze the image above.")));
        return new GetPromptResult(Optional.empty(), messages);
    }

    @McpTool(name = "wrong_tool_name", description = "test")
    public String wrongToolName()
    {
        return "OK";
    }

    @McpTool(name = "test_trigger_prompt_change", description = "test")
    public void testTriggerPromptChange(McpRequestContext requestContext)
    {
        Prompt prompt = entities.prompts(requestContext).getFirst();
        PromptEntry entry = entities.promptEntry(requestContext, prompt.name()).orElseThrow();
        Prompt changedPrompt = new Prompt(prompt.name(), Optional.of(prompt.description().orElse("") + Math.random()), prompt.role(), prompt.arguments(), prompt.icons());
        entities.addPrompt(changedPrompt, entry.promptHandler());
    }

    @McpTool(name = "test_trigger_tool_change", description = "test")
    public void testTriggerToolChange(McpRequestContext requestContext)
    {
        Tool tool = entities.tools(requestContext).getFirst();
        ToolEntry entry = entities.toolEntry(requestContext, tool.name()).orElseThrow();
        Tool changedTool = new Tool(tool.name(), Optional.of(tool.description().orElse("") + Math.random()), tool.title(), tool.inputSchema(), tool.outputSchema(), tool.annotations(), tool.icons(), tool.meta());
        entities.addTool(changedTool, entry.toolHandler());
    }

    public record InputRequiredResult(String name) {}

    @McpTool(name = "test_input_required_result_elicitation", description = "test")
    public CallToolResult testInputRequiredResultElicitation(InputResponses inputResponses)
    {
        return inputResponses.getInputResponse("user_name")
                .map(value -> jsonMapper.convertValue(value, InputRequiredResult.class))
                .map(userName -> new CallToolResult(new TextContent("Hello, %s!".formatted(userName.name))))
                .orElseGet(() -> {
                    ObjectNode schema = new JsonSchemaBuilder().build(InputRequiredResult.class);
                    ElicitRequestForm elicitRequestForm = new ElicitRequestForm("What is your name?", schema);
                    return CallToolResult.inputRequestsBuilder()
                            .add("user_name", METHOD_ELICITATION_CREATE, elicitRequestForm)
                            .build();
                });
    }

    @McpTool(name = "test_input_required_result_sampling", description = "test")
    public CallToolResult inputRequiredResultBasicSampling(InputResponses inputResponses)
    {
        return inputResponses.getInputResponse("capital_question")
                .map(value -> jsonMapper.convertValue(value, CreateMessageResult.class))
                .map(messageResult -> {
                    String responseText = (messageResult.content() instanceof TextContent textContent) ? textContent.text() : "No response";
                    return new CallToolResult(new TextContent(responseText));
                })
                .orElseGet(() -> {
                    CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent("What is the capital of France?"), 100);
                    return CallToolResult.inputRequestsBuilder()
                            .add("capital_question", METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest)
                            .build();
                });
    }

    @McpTool(name = "test_input_required_result_list_roots", description = "test")
    public CallToolResult testInputRequiredResultListRoots(InputResponses inputResponses)
    {
        return inputResponses.getInputResponse("client_roots")
                .map(value -> jsonMapper.convertValue(value, ListRootsResult.class))
                .map(roots -> {
                    List<Content> rootUris = roots.roots().stream()
                            .map(root -> new TextContent(root.uri()))
                            .collect(toImmutableList());
                    return new CallToolResult(rootUris);
                })
                .orElseGet(() -> CallToolResult.inputRequestsBuilder()
                        .add("client_roots", METHOD_ROOTS_LIST, ImmutableMap.of())
                        .build());
    }

    public record Confirm(boolean ok) {}

    @McpTool(name = "test_input_required_result_request_state", description = "MRTR tests")
    public CallToolResult testInputRequiredResultRequestState(InputResponses inputResponses)
    {
        Optional<ElicitResult> elicitResult = inputResponses.getInputResponse("confirm")
                .map(value -> jsonMapper.convertValue(value, ElicitResult.class));
        if (elicitResult.isPresent()) {
            boolean success = (elicitResult.orElseThrow().action() == ElicitResult.Action.ACCEPT) && inputResponses.requestState().orElse("").equals("booga-booga");
            return new CallToolResult(new TextContent(success ? "state-ok" : "state-not-ok"));
        }

        ObjectNode objectNode = new JsonSchemaBuilder().build(Confirm.class);
        return CallToolResult.inputRequestsBuilder()
                .withRequestState("booga-booga")
                .add("confirm", METHOD_ELICITATION_CREATE, new ElicitRequestForm("Please confirm", objectNode))
                .build();
    }

    public record PromptContext(String context) {}

    @McpPrompt(name = "test_input_required_result_prompt", description = "MRTR tests")
    public GetPromptResult testInputRequiredResultPrompt(InputResponses inputResponses)
    {
        Optional<ElicitResult> elicitResult = inputResponses.getInputResponse("user_context")
                .map(value -> jsonMapper.convertValue(value, ElicitResult.class));
        if (elicitResult.isPresent() && (elicitResult.orElseThrow().action() == ElicitResult.Action.ACCEPT) && inputResponses.requestState().orElse("").equals("beep-bop")) {
            return new GetPromptResult("Yep, it worked");
        }

        ObjectNode objectNode = new JsonSchemaBuilder().build(PromptContext.class);
        return GetPromptResult.inputRequestsBuilder()
                .withRequestState("beep-bop")
                .add("user_context", METHOD_ELICITATION_CREATE, new ElicitRequestForm("What context should the prompt use?", objectNode))
                .build();
    }

    @McpTool(name = "test_input_required_result_multiple_inputs", description = "test")
    public CallToolResult testInputRequiredResultMultipleInputs(InputResponses inputResponses)
    {
        Optional<InputRequiredResult> maybeUserName = inputResponses.getInputResponse("user_name").map(value -> jsonMapper.convertValue(value, InputRequiredResult.class));
        Optional<CreateMessageResult> maybeGreeting = inputResponses.getInputResponse("greeting").map(value -> jsonMapper.convertValue(value, CreateMessageResult.class));
        Optional<ListRootsResult> maybeClientRoots = inputResponses.getInputResponse("client_roots").map(value -> jsonMapper.convertValue(value, ListRootsResult.class));
        return maybeUserName.flatMap(userName -> maybeGreeting.flatMap(greeting -> maybeClientRoots.map(clientRoots -> {
                    if (inputResponses.requestState().orElse("").equals("hey-you")) {
                        String result = "It worked. %s %s %s".formatted(userName.name, greeting.content(), clientRoots.roots().size());
                        return new CallToolResult(new TextContent(result));
                    }
                    return new CallToolResult(ImmutableList.of(new TextContent("Bad request state")), Optional.empty(), true);
                })))
                .orElseGet(() -> {
                    ObjectNode schema = new JsonSchemaBuilder().build(InputRequiredResult.class);
                    ElicitRequestForm elicitRequestForm = new ElicitRequestForm("What is your name?", schema);

                    CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent("Generate a greeting"), 100);

                    return CallToolResult.inputRequestsBuilder()
                            .withRequestState("hey-you")
                            .add("user_name", METHOD_ELICITATION_CREATE, elicitRequestForm)
                            .add("greeting", METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest)
                            .add("client_roots", METHOD_ROOTS_LIST, ImmutableMap.of())
                            .build();
                });
    }

    public record FavoriteColor(String color) {}

    @McpTool(name = "test_input_required_result_multi_round", description = "test")
    public CallToolResult testInputRequiredResultMultiRound(InputResponses inputResponses)
    {
        if (inputResponses.inputResponses().isEmpty()) {
            ObjectNode schema = new JsonSchemaBuilder().build(InputRequiredResult.class);
            ElicitRequestForm elicitRequestForm = new ElicitRequestForm("Step 1: What is your name?", schema);
            return CallToolResult.inputRequestsBuilder()
                    .withRequestState("yeah yeah yeah")
                    .add("step1", METHOD_ELICITATION_CREATE, elicitRequestForm)
                    .build();
        }

        if (inputResponses.inputResponsesMap().containsKey("step1")) {
            if (!inputResponses.requestState().orElse("").equals("yeah yeah yeah")) {
                return new CallToolResult(ImmutableList.of(new TextContent("Bad request state")), Optional.empty(), true);
            }
            ObjectNode schema = new JsonSchemaBuilder().build(FavoriteColor.class);
            ElicitRequestForm elicitRequestForm = new ElicitRequestForm("Step 2: What is your favorite color?", schema);
            return CallToolResult.inputRequestsBuilder()
                    .withRequestState("no no no")
                    .add("step2", METHOD_ELICITATION_CREATE, elicitRequestForm)
                    .build();
        }

        if (inputResponses.inputResponsesMap().containsKey("step2")) {
            if (!inputResponses.requestState().orElse("").equals("no no no")) {
                return new CallToolResult(ImmutableList.of(new TextContent("Bad request state")), Optional.empty(), true);
            }
            return new CallToolResult(new TextContent("It worked"));
        }

        return new CallToolResult(ImmutableList.of(new TextContent("Bad inputResponses")), Optional.empty(), true);
    }

    @McpTool(name = "test_input_required_result_tampered_state", description = "test")
    public CallToolResult testInputRequiredResultTamperedState(InputResponses inputResponses)
    {
        String hashedValue = Hashing.sha256().hashString("corned beef", UTF_8)
                .toString();

        return inputResponses.requestState().map(requestState -> {
                    if (requestState.equals(hashedValue)) {
                        return new CallToolResult(new TextContent("It worked"));
                    }
                    throw new Error("RequestState mismatch");
                })
                .orElseGet(() -> {
                    ObjectNode schema = new JsonSchemaBuilder().build(InputRequiredResult.class);
                    ElicitRequestForm elicitRequestForm = new ElicitRequestForm("What is your name?", schema);
                    return CallToolResult.inputRequestsBuilder()
                            .withRequestState(hashedValue)
                            .add("user_name", METHOD_ELICITATION_CREATE, elicitRequestForm)
                            .build();
                });
    }

    @McpTool(name = "test_input_required_result_capabilities", description = "test")
    public CallToolResult testInputRequiredResultCapabilities(McpRequestContext requestContext)
    {
        InputRequests.Builder<CallToolResult> builder = CallToolResult.inputRequestsBuilder();

        if (requestContext.clientCapabilities().elicitation().isPresent()) {
            ObjectNode schema = new JsonSchemaBuilder().build(InputRequiredResult.class);
            ElicitRequestForm elicitRequestForm = new ElicitRequestForm("What is your name?", schema);
            builder.add("user_name", METHOD_ELICITATION_CREATE, elicitRequestForm);
        }

        if (requestContext.clientCapabilities().sampling().isPresent()) {
            CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent("Generate a greeting"), 100);
            builder.add("greeting", METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest);
        }

        return builder.build();
    }
}

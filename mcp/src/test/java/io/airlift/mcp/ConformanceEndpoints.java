package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
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
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Role;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeoutException;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Constants.METHOD_SAMPLING_CREATE_MESSAGE;
import static io.airlift.mcp.model.LoggingLevel.INFO;
import static java.util.Objects.requireNonNull;

// see: https://github.com/modelcontextprotocol/conformance/blob/main/examples/servers/typescript/everything-server.ts
public class ConformanceEndpoints
{
    private static final String TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
    private static final String TEST_AUDIO_BASE64 = "UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAAB9AAACABAAZGF0YQIAAAA=";

    private final ObjectMapper objectMapper;

    @Inject
    public ConformanceEndpoints(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
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
    public String testSampling(McpRequestContext requestContext, @McpDescription("The prompt to send to the LLM") String prompt)
            throws InterruptedException, TimeoutException
    {
        CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent(prompt), 100);
        JsonRpcResponse<CreateMessageResult> response = requestContext.serverToClientRequest(METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest, CreateMessageResult.class, Duration.ofMinutes(1), Duration.ofSeconds(1));
        String responseText = response.result().map(messageResult -> (messageResult.content() instanceof TextContent textContent) ? textContent.text() : "No response").orElse("No response");
        return "LLM response: " + responseText;
    }

    public record TestElicitation(String response) {}

    @McpTool(name = "test_elicitation", description = "Tests server-initiated elicitation (user input request)")
    public String testElicitation(McpRequestContext requestContext, @McpDescription("The message to show the user") String message)
            throws InterruptedException, TimeoutException
    {
        ObjectNode schema = new JsonSchemaBuilder("testElicitation").build(Optional.of("User's response"), TestElicitation.class);
        ElicitRequestForm elicitRequestForm = new ElicitRequestForm(message, schema);
        JsonRpcResponse<ElicitResult> response = requestContext.serverToClientRequest(METHOD_ELICITATION_CREATE, elicitRequestForm, ElicitResult.class, Duration.ofMinutes(5), Duration.ofSeconds(1));
        return response.result().map(result -> "User response: action=%s, content=%s".formatted(result.action(), mapToJson(result.content())))
                .orElse("No response");
    }

    private String mapToJson(Optional<Map<String, Object>> map)
    {
        try {
            return objectMapper.writeValueAsString(map.orElseGet(ImmutableMap::of));
        }
        catch (JacksonException e) {
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
        String json = objectMapper.writeValueAsString(ImmutableMap.of("id", id, "templateTest", true, "data", "Data for ID: " + id));
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
}

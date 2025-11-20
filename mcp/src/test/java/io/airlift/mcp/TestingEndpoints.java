package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.SamplingRequest;
import io.airlift.mcp.model.SamplingResult;
import io.airlift.mcp.model.StructuredContentResult;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolExecution;
import io.airlift.mcp.tasks.TaskId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonSchemaBuilder.forRecord;
import static io.airlift.mcp.model.Task.META_KEY_RELATED_TASK;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class TestingEndpoints
{
    private final McpServer mcpServer;
    private final MockAppTaskProcessor appTaskProcessor;
    private final ObjectMapper objectMapper;

    @Inject
    public TestingEndpoints(McpServer mcpServer, MockAppTaskProcessor appTaskProcessor, ObjectMapper objectMapper)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.appTaskProcessor = requireNonNull(appTaskProcessor, "appTaskProcessor is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @McpTool(name = "add", description = "Add two numbers")
    public int add(TestingIdentity testingIdentity, int a, int b)
    {
        assertThat(testingIdentity.name()).isEqualTo("Mr. Tester");

        return a + b;
    }

    @McpTool(name = "progress", description = "Test progress notifications")
    public boolean toolWithNotifications(McpRequestContext requestContext)
    {
        for (int i = 0; i <= 100; ++i) {
            requestContext.sendProgress(i, 100, "Progress " + i + "%");
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @McpTool(name = "throws", description = "Throws an exception for testing purposes")
    public void throwsException()
    {
        throw exception("this ain't good");
    }

    @McpTool(name = "log", description = "Test logging")
    public void testLogging(McpRequestContext requestContext)
    {
        requestContext.sendLog(LoggingLevel.DEBUG, "This is debug");
        requestContext.sendLog(LoggingLevel.ALERT, "This is alert");
    }

    public record FullName(String firstName, String lastName) {}

    @McpTool(name = "testElicitation", description = "Test elicitation", taskSupport = ToolExecution.REQUIRED)
    public CallToolResult testElicitation(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        if (callToolRequest.task().isEmpty()) {
            return new CallToolResult("Tool request did not indicate creating a task");
        }

        Task task = appTaskProcessor.createTask(requestContext, callToolRequest);
        TaskId taskId = new TaskId(task.taskId());

        ElicitRequest elicitRequest = new ElicitRequest("Tell us who you are", forRecord(TestingEndpoints.FullName.class), Optional.of(ImmutableMap.of(META_KEY_RELATED_TASK, task.taskId())));
        appTaskProcessor.setPendingRequest(taskId, "elicitation/create", elicitRequest, ElicitResult.class, elicitResult -> appTaskProcessor.completeTask(taskId, mapElicitationResult(elicitResult)));

        return new CallToolResult(task);
    }

    @McpTool(name = "elicitationThenSample", description = "Test elicitation, then sampling", taskSupport = ToolExecution.REQUIRED)
    public CallToolResult testElicitationThenSample(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        if (callToolRequest.task().isEmpty()) {
            return new CallToolResult("Tool request did not indicate creating a task");
        }

        Task task = appTaskProcessor.createTask(requestContext, callToolRequest);
        TaskId taskId = new TaskId(task.taskId());

        ElicitRequest elicitRequest = new ElicitRequest("Tell us who you are", forRecord(TestingEndpoints.FullName.class), Optional.of(ImmutableMap.of(META_KEY_RELATED_TASK, task.taskId())));
        appTaskProcessor.setPendingRequest(taskId, "elicitation/create", elicitRequest, ElicitResult.class, elicitResult -> {
            CallToolResult callToolResult = mapElicitationResult(elicitResult);
            if (callToolResult.isError()) {
                appTaskProcessor.completeTask(taskId, callToolResult);
            }
            else {
                SamplingRequest samplingRequest = new SamplingRequest(((TextContent) callToolResult.content().getFirst()).text(), 100);
                appTaskProcessor.setPendingRequest(taskId, "sampling/createMessage", samplingRequest, SamplingResult.class, maybeSamplingResult -> {
                    CallToolResult finalCallToolResult = maybeSamplingResult.map(samplingResult -> samplingResult.stopReason()
                                    .map(stopReason -> new CallToolResult("Stopped: " + stopReason)).orElseGet(() -> new CallToolResult(samplingResult.content())))
                            .orElseGet(() -> CallToolResult.error("Sampling failed"));
                    appTaskProcessor.completeTask(taskId, finalCallToolResult);
                });
            }
        });

        return new CallToolResult(task);
    }

    @McpTool(name = "taskThatThrows", taskSupport = ToolExecution.REQUIRED)
    public CallToolResult toolThatThrows(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        Task task = appTaskProcessor.createTask(requestContext, callToolRequest);
        TaskId taskId = new TaskId(task.taskId());

        ElicitRequest elicitRequest = new ElicitRequest("dummy", forRecord(TestingEndpoints.FullName.class), Optional.of(ImmutableMap.of(META_KEY_RELATED_TASK, task.taskId())));
        appTaskProcessor.setPendingRequest(taskId, "elicitation/create", elicitRequest, ElicitResult.class, elicitResult -> {
            throw new IllegalArgumentException("Things didn't go well");
        });

        return new CallToolResult(task);
    }

    private CallToolResult mapElicitationResult(ElicitResult elicitResult)
    {
        if (elicitResult.action() != ElicitResult.Action.ACCEPT) {
            return CallToolResult.error(elicitResult.action().name());
        }

        return elicitResult.map(objectMapper, FullName.class)
                .map(fullName -> new CallToolResult(fullName.firstName() + ", " + fullName.lastName()))
                .orElseGet(() -> CallToolResult.error(elicitResult.action().name()));
    }

    private CallToolResult mapElicitationResult(Optional<ElicitResult> maybeElicitResult)
    {
        return maybeElicitResult.map(this::mapElicitationResult)
                .orElseGet(() -> CallToolResult.error("Elicitation failed"));
    }

    @McpPrompt(name = "greeting", description = "Generate a greeting message")
    public String greeting(@McpDescription("Name of the person to greet") String name)
    {
        return "Hello, " + name + "!";
    }

    @McpResource(name = "example1", uri = "file://example1.txt", description = "This is example1 resource.", mimeType = "text/plain")
    public ResourceContents example1Resource()
    {
        return new ResourceContents("foo2", "file://example1.txt", "text/plain", "This is the content of file://example1.txt");
    }

    @McpResource(name = "example2", uri = "file://example2.txt", description = "This is example2 resource.", mimeType = "text/plain")
    public ResourceContents example2Resource()
    {
        return new ResourceContents("foo2", "file://example2.txt", "text/plain", "This is the content of file://example2.txt");
    }

    @McpResourceTemplate(name = "template", uriTemplate = "file://{id}.template", description = "This is a resource template", mimeType = "text/plain")
    public List<ResourceContents> exampleResourceTemplate(ReadResourceRequest request, ResourceTemplateValues resourceTemplateValues)
    {
        String id = resourceTemplateValues.templateValues().getOrDefault("id", "n/a");
        return ImmutableList.of(new ResourceContents(request.uri(), request.uri(), "text/plain", "ID is: " + id));
    }

    @McpTool(name = "addThree", description = "Add three numbers")
    public CallToolResult addThree(TestingIdentity testingIdentity, int a, int b, int c)
    {
        assertThat(testingIdentity.name()).isEqualTo("Mr. Tester");

        return new CallToolResult(String.valueOf(a + b + c));
    }

    public record TwoAndThree(int firstTwo, int allThree) {}

    @McpTool(name = "addFirstTwoAndAllThree", description = "Add the first two numbers together, and potentially all three numbers together. Numbers must be >= 0")
    public StructuredContentResult<TwoAndThree> addTwoAndThree(TestingIdentity testingIdentity, int a, int b, Optional<Integer> c)
    {
        assertThat(testingIdentity.name()).isEqualTo("Mr. Tester");

        if (a < 0 || b < 0 || c.map(num -> num < 0).orElse(false)) {
            return new StructuredContentResult<>(
                    ImmutableList.of(new TextContent("Negative numbers are not allowed")),
                    Optional.empty(),
                    true);
        }

        int firstTwo = a + b;
        int allThree = firstTwo + c.orElse(0);

        return new StructuredContentResult<>(
                ImmutableList.of(new TextContent(String.valueOf(allThree))),
                new TwoAndThree(firstTwo, allThree),
                false);
    }
}

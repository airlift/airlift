package io.airlift.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.CreateMessageRequest;
import io.airlift.mcp.model.CreateMessageRequest.ContextInclusionStrategy;
import io.airlift.mcp.model.CreateMessageRequest.SamplingMessage;
import io.airlift.mcp.model.CreateMessageResult;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.StructuredContentResult;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Role.USER;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class TestingEndpoints
{
    private final McpServer mcpServer;
    private final McpJsonSchemaMapper mcpJsonSchemaMapper;

    @Inject
    public TestingEndpoints(McpServer mcpServer, McpJsonSchemaMapper mcpJsonSchemaMapper)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.mcpJsonSchemaMapper = requireNonNull(mcpJsonSchemaMapper, "mcpJsonSchemaMapper is null");
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

    @McpTool(name = "logging", description = "Test logging")
    public void testLogging(McpRequestContext requestContext)
    {
        requestContext.sendLog(LoggingLevel.DEBUG, TestingEndpoints.class.getName(), "This is DEBUG");
        requestContext.sendLog(LoggingLevel.NOTICE, TestingEndpoints.class.getName(), "This is NOTICE");
    }

    @McpTool(name = "addResource", description = "Add a new resource")
    public void addResource(String name, String uri)
    {
        Resource resource = new Resource(name, uri, Optional.empty(), "text/plain", OptionalLong.empty(), Optional.empty());
        ResourceHandler handler = (requestContext, sourceResource, readResourceRequest) -> ImmutableList.of(new ResourceContents(name, uri, "text/plain", "This is the content of " + uri));
        mcpServer.addResource(resource, handler);
    }

    @McpTool(name = "changeExampleResource", description = "Send a change for example1.txt")
    public void changeExampleResource()
    {
        mcpServer.notifyResourceChanged("file://example1.txt");
    }

    @McpTool(name = "showCurrentRoots", description = "Show the roots value for this session")
    public String showCurrentRoots(McpRequestContext requestContext)
    {
        return requestContext.roots(Duration.ofMinutes(1))
                .stream()
                .map(ListRootsResult.Root::uri)
                .collect(joining(", "));
    }

    @McpTool(name = "samplingTest")
    public String samplingTest(McpRequestContext requestContext)
    {
        List<SamplingMessage> messages = List.of(new SamplingMessage(USER, new TextContent("Hey hey ho ho")));
        CreateMessageRequest createMessageRequest = new CreateMessageRequest(messages, Optional.empty(), Optional.empty(), ContextInclusionStrategy.none, OptionalDouble.empty(), OptionalInt.of(10), ImmutableList.of(), ImmutableMap.of());
        try {
            CreateMessageResult response = requestContext.serverToClientRequest("sampling/createMessage", createMessageRequest, new TypeReference<>() {}, Duration.ofMinutes(5));
            return "Accepted";
        }
        catch (McpException e) {
            return e.errorDetail().message();
        }
    }

    public record Feedback(String name, String comments) {}

    @McpTool(name = "elicitationTest")
    public String elicitationTest(McpRequestContext requestContext)
    {
        ElicitRequest request = new ElicitRequest("Give us your feedback", mcpJsonSchemaMapper.toJsonSchema(Feedback.class));
        ElicitResult<Feedback> response = requestContext.serverToClientRequest("elicitation/create", request, new TypeReference<>() {}, Duration.ofMinutes(5));
        if (response.action() != ElicitResult.Action.accept) {
            return response.action().name();
        }
        return response.content().toString();
    }

    @McpTool(name = "throws", description = "Throws an exception for testing purposes")
    public void throwsException()
    {
        throw exception("this ain't good");
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

        return new CallToolResult(ImmutableList.of(new TextContent(String.valueOf(a + b + c))),
                Optional.empty(),
                false);
    }

    public record TwoAndThree(int firstTwo, int allThree) {}

    @McpTool(name = "addFirstTwoAndAllThree", description = "Add the first two numbers together, and add all three numbers together. Numbers must be >= 0")
    public StructuredContentResult<TwoAndThree> addTwoAndThree(TestingIdentity testingIdentity, int a, int b, int c)
    {
        assertThat(testingIdentity.name()).isEqualTo("Mr. Tester");

        if (a < 0 || b < 0 || c < 0) {
            return new StructuredContentResult<>(
                    ImmutableList.of(new TextContent("Negative numbers are not allowed")),
                    Optional.empty(),
                    true);
        }

        return new StructuredContentResult<>(
                ImmutableList.of(new TextContent(String.valueOf(a + b + c))),
                new TwoAndThree(a + b, a + b + c),
                false);
    }
}

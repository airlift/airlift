package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.StructuredContentResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class TestingEndpoints
{
    private final McpServer mcpServer;

    @Inject
    public TestingEndpoints(McpServer mcpServer)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
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
        run100Times(i -> requestContext.sendProgress(i, 100, "Progress " + i + "%"));
        return true;
    }

    @McpTool(name = "pings", description = "Test progress notifications")
    public boolean toolWithPings(McpRequestContext requestContext)
    {
        run100Times(_ -> requestContext.ping());
        return false;
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

        return new CallToolResult(ImmutableList.of(new Content.TextContent(String.valueOf(a + b + c))),
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
                    ImmutableList.of(new Content.TextContent("Negative numbers are not allowed")),
                    Optional.empty(),
                    true);
        }

        return new StructuredContentResult<>(
                ImmutableList.of(new Content.TextContent(String.valueOf(a + b + c))),
                new TwoAndThree(a + b, a + b + c),
                false);
    }

    private void run100Times(IntConsumer runner)
    {
        for (int i = 0; i <= 100; ++i) {
            runner.accept(i);
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}

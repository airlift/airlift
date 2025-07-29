package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.mcp.Person.Address;
import io.airlift.mcp.handler.ResourceTemplateHandler.PathTemplateValues;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.CreateMessageRequest;
import io.airlift.mcp.model.CreateMessageResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.ModelHint;
import io.airlift.mcp.model.ModelPreferences;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.SamplingMessage;
import io.airlift.mcp.session.ListType;
import io.airlift.mcp.session.SessionId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;

public class TestingEndpoints
{
    private final TestingSessionController sessionController;

    @Inject
    public TestingEndpoints(TestingSessionController sessionController)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @McpTool(name = "add")
    public int add(@McpDescription("X marks the spot") int x, @McpDescription("Because we like you") int y)
    {
        return x + y;
    }

    @McpTool(name = "uppercase", description = "Convert a string to uppercase")
    public String uppercase(String input)
    {
        return input.toUpperCase(Locale.ROOT);
    }

    @McpTool(name = "uppercaseSoon", description = "Convert a string to uppercase eventually")
    public String uppercaseWithProgress(String input, McpNotifier notifier)
    {
        sendProgress(notifier);
        return input.toUpperCase(Locale.ROOT);
    }

    @McpTool(name = "lookupPerson")
    public Address lookupPerson(Person person)
    {
        return person.address();
    }

    @McpTool(name = "itsSimple", description = "It's just so simple")
    public int itsSimple(@McpDescription("A simple thing") SimpleThing ignore)
    {
        return 0;
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

    @McpTool(name = "logging", description = "Do some logging (at the \"warning\" level")
    public void doLogging(McpNotifier notifier)
    {
        notifier.sendLog(LoggingLevel.warning, "This is a warning message", new SimpleThing(ImmutableList.of("a", "b"), 123.456));
    }

    @McpTool(name = "showCurrentRoots", description = "List the current roots")
    public CallToolResult showCurrentRoots(SessionId sessionId)
    {
        List<String> roots = sessionController.roots(sessionId);
        List<Content> content = roots
                .stream()
                .map(TextContent::new)
                .collect(toImmutableList());
        return new CallToolResult(content);
    }

    @McpTool(name = "sendListChangedEvents", description = "Simulate sending list changed events")
    public void sendListChangedNotification(SessionId sessionId)
    {
        sessionController.simulateListChanged(sessionId, ListType.RESOURCES);
        sessionController.simulateListChanged(sessionId, ListType.PROMPTS);
        sessionController.simulateListChanged(sessionId, ListType.TOOLS);
    }

    @McpTool(name = "takeCreateMessageResults", description = "Show the results of create message requests")
    public CallToolResult takeCreateMessageResults(SessionId sessionId)
    {
        List<CreateMessageResult> createMessageResults = sessionController.takeCreateMessageResults(sessionId);
        List<Content> content = createMessageResults.stream()
                .map(CreateMessageResult::content)
                .collect(toImmutableList());
        return new CallToolResult(content);
    }

    @McpTool(name = "sendResourcesUpdatedNotification", description = "Simulate sending resource updated notifications")
    public String sendResourcesUpdatedNotification(SessionId sessionId, String uri)
    {
        return sessionController.simulateResourcesUpdated(sessionId, uri)
                ? "Resources updated notification sent for URI: " + uri
                : "You are not subscribed to: " + uri;
    }

    @McpTool(name = "sendSamplingMessage", description = "Simulate sending a sampling message")
    public void sendSamplingMessage(McpNotifier notifier, SessionId sessionId)
    {
        List<SamplingMessage> messages = ImmutableList.of(new SamplingMessage(Role.user, new TextContent("This is some content")));
        ModelPreferences modelPreferences = new ModelPreferences(new ModelHint("Only a test"));
        CreateMessageRequest createMessageRequest = new CreateMessageRequest(messages, modelPreferences, "This is only a test", 500);
        JsonRpcRequest<CreateMessageRequest> request = JsonRpcRequest.buildRequest("sendSamplingMessage", "sampling/createMessage", createMessageRequest);
        sessionController.acceptClientToServerRequest(sessionId, request);
        notifier.sendRequest(request);
    }

    @McpResourceTemplate(name = "template1", uriTemplate = "file://{part}.txt", description = "This is a template resource.", mimeType = "text/plain")
    public List<ResourceContents> listResourceTemplates(ReadResourceRequest readResourceRequest, PathTemplateValues pathTemplateValues)
    {
        if (readResourceRequest.uri().equals("file://one.txt")) {
            ResourceContents contents = new ResourceContents("foo2", readResourceRequest.uri(), "text/plain", pathTemplateValues.values().get("part"));
            return ImmutableList.of(contents);
        }
        return ImmutableList.of();
    }

    @McpPrompt(name = "greeting", description = "Generate a greeting message")
    public String greeting(@McpDescription("Name of the person to greet") String name)
    {
        return "Hello, " + name + "!";
    }

    @McpPrompt(name = "progress", description = "Generate a greeting message after some time")
    public String greetingWithProgress(@McpDescription("Name of the person to greet") String name, McpNotifier notifier)
    {
        sendProgress(notifier);
        return "Hello, " + name + "!";
    }

    @McpPrompt(name = "testCancellation", description = "Loops forever until cancelled")
    public String testCancellation(McpNotifier notifier)
    {
        while (!notifier.cancellationRequested()) {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            notifier.notifyProgress("Waiting for cancellation...", Optional.empty(), Optional.empty());
        }

        notifier.notifyProgress("Cancelled!", Optional.empty(), Optional.empty());
        return "Cancelled!";
    }

    @McpTool(name = "throws", description = "Throws an exception")
    public String throwException()
    {
        throw exception("This didn't work");
    }

    @McpCompletion(name = "completePrompt")
    public Optional<Completion> completePrompt(CompletionRequest prompt)
    {
        return switch (prompt.ref()) {
            case CompletionReference.Prompt promptRef -> {
                if (promptRef.name().equals("greeting")) {
                    yield Optional.of(new Completion(ImmutableList.of("yo", "hey")));
                }
                yield Optional.empty();
            }

            case CompletionReference.Resource _ -> Optional.of(new Completion(ImmutableList.of("one", "two")));
        };
    }

    private static void sendProgress(McpNotifier notifier)
    {
        final int qty = 1000;
        for (int i = 0; i < qty; i++) {
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            notifier.notifyProgress("Processing... ", Optional.of((double) i + 1), Optional.of((double) qty));
        }
    }
}

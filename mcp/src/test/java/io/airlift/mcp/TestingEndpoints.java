package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.Person.Address;
import io.airlift.mcp.handler.ResourceTemplatesEntry;
import io.airlift.mcp.handler.ResourcesEntry;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;

import java.util.Locale;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;

public class TestingEndpoints
{
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
    public int itsSimple(@McpDescription("A simple thing") SimpleThing thing)
    {
        return 0;
    }

    @McpResources
    public ResourcesEntry listResources()
    {
        Resource resource1 = new Resource("example1", "file://example1.txt", Optional.of("This is example1 resource."), "text/plain", Optional.empty(), Optional.empty());
        Resource resource2 = new Resource("example2", "file://example2.txt", Optional.of("This is example2 resource."), "text/plain", Optional.empty(), Optional.empty());

        return new ResourcesEntry(ImmutableList.of(resource1, resource2), (_, notifier, _, readResourceRequest) -> {
            if (readResourceRequest.uri().contains("example2")) {
                sendProgress(notifier);
            }

            ResourceContents contents = new ResourceContents("foo2", readResourceRequest.uri(), "text/plain", "This is the content of " + readResourceRequest.uri());
            return ImmutableList.of(contents);
        });
    }

    @McpResources
    public ResourceTemplatesEntry listResourceTemplates()
    {
        ResourceTemplate resource = new ResourceTemplate("example1", "file://{part}.txt", Optional.of("This is example1 resource."), "text/plain", Optional.empty(), Optional.empty());
        return new ResourceTemplatesEntry(ImmutableList.of(resource), (_, _, _, readResourceRequest, pathTemplateValues) -> {
            if (readResourceRequest.uri().equals("file://one.txt")) {
                ResourceContents contents = new ResourceContents("foo2", readResourceRequest.uri(), "text/plain", pathTemplateValues.get("part"));
                return ImmutableList.of(contents);
            }
            return ImmutableList.of();
        });
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

    @McpTool(name = "throws", description = "Throws an exception")
    public String throwException()
    {
        throw exception("This didn't work");
    }

    @McpCompletion
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

package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.Person.Address;
import io.airlift.mcp.handler.ResourceTemplateHandler.PathTemplateValues;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionReference;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;

import java.util.List;
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

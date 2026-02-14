package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest.CompleteArgument;
import io.airlift.mcp.model.CompleteRequest.CompleteContext;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.CreateMessageRequest;
import io.airlift.mcp.model.CreateMessageResult;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.Root;
import io.airlift.mcp.model.StructuredContentResult;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.tasks.TaskFacade;
import io.airlift.mcp.tasks.TaskMessageBuilder;
import io.airlift.mcp.tasks.Tasks;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Constants.METHOD_SAMPLING_CREATE_MESSAGE;
import static io.airlift.mcp.model.ElicitResult.Action.ACCEPT;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.tasks.TaskConditions.hasResponseWithId;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class TestingEndpoints
{
    private static final Logger log = Logger.get(TestingEndpoints.class);

    private final McpServer mcpServer;
    private final Set<ToolEntry> tools;
    private final Set<PromptEntry> prompts;
    private final Set<ResourceEntry> resources;
    private final SleepToolController sleepToolController;
    private final ObjectMapper objectMapper;
    private volatile String example2Content = "This is the content of file://example2.txt";
    private volatile String example1Content = "This is the content of file://example1.txt";

    @Inject
    public TestingEndpoints(
            McpServer mcpServer,
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            SleepToolController sleepToolController,
            ObjectMapper objectMapper)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");

        this.tools = ImmutableSet.copyOf(tools);
        this.prompts = ImmutableSet.copyOf(prompts);
        this.resources = ImmutableSet.copyOf(resources);
        this.sleepToolController = requireNonNull(sleepToolController, "sleepToolController is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @McpTool(name = "add", description = "Add two numbers", icons = "google")
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

    @McpPrompt(name = "age", description = "What is your age?", icons = "google")
    public String age(@McpDescription("What is your age?") String age)
    {
        return "You are " + age + "years old.";
    }

    @McpPrompt(name = "greeting", description = "Generate a greeting message")
    public String greeting(@McpDescription("Name of the person to greet") String name)
    {
        return "Hello, " + name + "!";
    }

    @McpPromptCompletion(name = "greeting")
    public List<String> nameCompletions(CompleteArgument argument, CompleteContext context)
    {
        if (argument.name().equals("name")) {
            return ImmutableList.of("Jordan", "Rita", "Bobby", "Oliver", "Olive", "Steve")
                    .stream()
                    .filter(name -> name.toLowerCase().startsWith(argument.value().toLowerCase()))
                    .collect(toImmutableList());
        }
        return ImmutableList.of();
    }

    @McpResource(name = "example1", uri = "file://example1.txt", description = "This is example1 resource.", mimeType = "text/plain", icons = "google")
    public ResourceContents example1Resource()
    {
        return new ResourceContents("foo2", "file://example1.txt", "text/plain", example1Content);
    }

    @McpResource(name = "example2", uri = "file://example2.txt", description = "This is example2 resource.", mimeType = "text/plain")
    public ResourceContents example2Resource()
    {
        return new ResourceContents("foo2", "file://example2.txt", "text/plain", example2Content);
    }

    @McpResourceTemplate(name = "template", uriTemplate = "file://{id}.template", description = "This is a resource template", mimeType = "text/plain", icons = "google")
    public List<ResourceContents> exampleResourceTemplate(ReadResourceRequest request, ResourceTemplateValues resourceTemplateValues)
    {
        String id = resourceTemplateValues.templateValues().getOrDefault("id", "n/a");
        return ImmutableList.of(new ResourceContents(request.uri(), request.uri(), "text/plain", "ID is: " + id));
    }

    @McpResourceTemplateCompletion(uriTemplate = "file://{id}.template")
    public List<String> example1ResourceCompletions(CompleteArgument argument)
    {
        if (argument.name().equals("id")) {
            return ImmutableList.of("manny", "moe", "jack")
                    .stream()
                    .filter(uri -> uri.toLowerCase().startsWith(argument.value().toLowerCase()))
                    .collect(toImmutableList());
        }
        return ImmutableList.of();
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

    @McpTool(name = "log", description = "Test logging")
    public void testLogging(McpRequestContext requestContext)
    {
        requestContext.sendLog(LoggingLevel.DEBUG, "This is debug");
        requestContext.sendLog(LoggingLevel.ALERT, "This is alert");
    }

    public enum VersionType
    {
        SYSTEM,
        RESOURCE,
    }

    @McpTool(name = "setVersion", description = "Update the version of a system list or resource")
    public void incrementListOrResource(VersionType type, String name)
    {
        switch (type) {
            case SYSTEM -> {
                switch (name) {
                    case "tools" -> {
                        ToolEntry firstTool = tools.iterator().next();
                        Tool alteredTool = new Tool(
                                firstTool.tool().name(),
                                firstTool.tool().description(),
                                Optional.of(UUID.randomUUID().toString()),
                                firstTool.tool().inputSchema(),
                                firstTool.tool().outputSchema(),
                                firstTool.tool().annotations(),
                                firstTool.tool().icons(),
                                firstTool.tool().execution(),
                                firstTool.tool().meta());
                        mcpServer.addTool(alteredTool, firstTool.toolHandler());
                    }

                    case "prompts" -> {
                        PromptEntry firstPrompt = prompts.iterator().next();
                        Prompt alteredPrompt = new Prompt(
                                firstPrompt.prompt().name(),
                                Optional.of(UUID.randomUUID().toString()),
                                firstPrompt.prompt().role(),
                                firstPrompt.prompt().arguments(),
                                firstPrompt.prompt().icons());
                        mcpServer.addPrompt(alteredPrompt, firstPrompt.promptHandler());
                    }

                    case "resources" -> {
                        ResourceEntry firstResource = resources.iterator().next();
                        Resource alteredResource = new Resource(
                                firstResource.resource().name(),
                                firstResource.resource().uri(),
                                Optional.of(UUID.randomUUID().toString()),
                                firstResource.resource().mimeType(),
                                firstResource.resource().size(),
                                firstResource.resource().annotations(),
                                firstResource.resource().icons());
                        mcpServer.addResource(alteredResource, firstResource.handler());
                    }

                    default -> throw new IllegalArgumentException("Unknown system session version name: " + name);
                }
            }
            case RESOURCE -> {
                switch (name) {
                    case "example1" -> example1Content = "Updated content of file://example1.txt: " + UUID.randomUUID();
                    case "example2" -> example2Content = "Updated content of file://example2.txt: " + UUID.randomUUID();
                    default -> throw new IllegalArgumentException("Unknown resource session version name: " + name);
                }
            }
        }
    }

    @McpTool(name = "sleep", description = "Sleep for a specified number of seconds")
    public String sleepForSeconds(String name, int secondsToSleep)
    {
        sleepToolController.startedLatch().release();

        long msToSleep = TimeUnit.SECONDS.toMillis(secondsToSleep);

        try {
            while (true) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                String exitName = sleepToolController.namesThatShouldExit().poll(msToSleep, MILLISECONDS);
                msToSleep -= stopwatch.elapsed(MILLISECONDS);

                if ((exitName == null) || (msToSleep <= 0)) {
                    return "timeout";
                }

                if (exitName.equals(name)) {
                    return "success";
                }

                // it's not ours - add it back
                sleepToolController.namesThatShouldExit().add(exitName);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        finally {
            sleepToolController.namesThatHaveExited().add(name);
        }
    }

    public record Person(String firstName, String lastName) {}

    @McpTool(name = "elicitation", description = "Test elicitation")
    public String elicitation(McpRequestContext requestContext)
    {
        if (requestContext.clientCapabilities().elicitation().isEmpty()) {
            throw new RuntimeException("Client does not support elicitation");
        }

        ObjectNode elicitation = new JsonSchemaBuilder("elicitation").build(Optional.empty(), Person.class);
        ElicitRequestForm elicitRequest = new ElicitRequestForm("Who are you?", elicitation);
        JsonRpcResponse<ElicitResult> response;
        try {
            response = requestContext.serverToClientRequest(METHOD_ELICITATION_CREATE, elicitRequest, ElicitResult.class, Duration.ofMinutes(5), Duration.ofSeconds(30));
        }
        catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        ElicitResult elicitResult = response.result().orElseThrow();
        return processElicitResult(elicitResult);
    }

    private static String processElicitResult(ElicitResult elicitResult)
    {
        if (elicitResult.action() != ACCEPT) {
            return elicitResult.action().toJsonValue();
        }
        return elicitResult.content()
                .map(contentMap -> "Hello, " + contentMap.get("firstName") + " " + contentMap.get("lastName") + "!")
                .orElse("No content");
    }

    @McpTool(name = "sampling", description = "Test sampling")
    public String sampling(McpRequestContext requestContext)
    {
        if (requestContext.clientCapabilities().sampling().isEmpty()) {
            throw new RuntimeException("Client does not support sampling");
        }

        CreateMessageRequest createMessageRequest = new CreateMessageRequest(Role.USER, new TextContent("Are you sure?"), 100);
        JsonRpcResponse<CreateMessageResult> response;
        try {
            response = requestContext.serverToClientRequest(METHOD_SAMPLING_CREATE_MESSAGE, createMessageRequest, CreateMessageResult.class, Duration.ofMinutes(5), Duration.ofSeconds(1));
        }
        catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        if (response.error().isPresent()) {
            return response.error().orElseThrow().message();
        }

        CreateMessageResult createMessageResult = response.result().orElseThrow();
        if (createMessageResult.stopReason().isPresent()) {
            return "Stopped: " + createMessageResult.stopReason().orElseThrow().toJsonValue();
        }

        if (createMessageResult.content() instanceof TextContent(var text, _)) {
            return "Response: " + text;
        }

        return "Response: unknown";
    }

    @McpTool(name = "task", description = "Test task with elicitation")
    public Task testTask(McpRequestContext requestContext)
    {
        Tasks tasks = requestContext.tasks();

        TaskFacade task = tasks.createTask(Optional.of(Duration.ofMillis(100)), Optional.empty());
        newVirtualThreadPerTaskExecutor().execute(() -> tasks.executeCancellable(task.taskId(), () -> {
            try {
                Thread.sleep(1000);

                ObjectNode elicitation = new JsonSchemaBuilder("elicitation").build(Optional.empty(), Person.class);
                ElicitRequestForm elicitRequest = new ElicitRequestForm("Who are you?", elicitation);
                JsonRpcRequest<ElicitRequestForm> rpcRequest = TaskMessageBuilder.builder(task.taskId()).buildRequest(METHOD_ELICITATION_CREATE, elicitRequest, ElicitRequestForm.class);
                tasks.setTaskMessage(task.taskId(), rpcRequest, Optional.empty());

                TaskFacade updatedTask = tasks.blockUntil(task.taskId(), Duration.ofMinutes(5), Duration.ofSeconds(1), hasResponseWithId(rpcRequest.id()));
                ElicitResult elicitResult = updatedTask.extractResponse(rpcRequest.id(), ElicitResult.class, objectMapper)
                        .orElseThrow(() -> new AssertionError("Response does not exist"));
                String resultText = processElicitResult(elicitResult);
                JsonRpcResponse<CallToolResult> response = TaskMessageBuilder.builder(task).buildResponse(new CallToolResult(new TextContent(resultText)), CallToolResult.class);
                tasks.completeTask(task.taskId(), response, Optional.empty());
            }
            catch (InterruptedException | TimeoutException _) {
                log.info("Task was interrupted or timed out: %s", task.taskId());
            }
            catch (Exception e) {
                JsonRpcResponse<?> error = TaskMessageBuilder.builder(task).buildError(new JsonRpcErrorDetail(INVALID_REQUEST, firstNonNull(e.getMessage(), "Unknown error")));
                tasks.completeTask(task.taskId(), error, Optional.of("Failed to get elicitation response"));
                throw new RuntimeException(e);
            }
        }));

        return task.toTask();
    }

    @McpTool(name = "roots", description = "List roots")
    public String listRoots(McpRequestContext requestContext)
    {
        try {
            return requestContext.requestRoots(Duration.ofMinutes(1), Duration.ofSeconds(30))
                    .stream()
                    .map(Root::uri)
                    .collect(joining(", "));
        }
        catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}

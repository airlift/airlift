[◀︎ Airlift](../README.md)

# MCP server support

## Introduction

This module provides support for creating [MCP servers](https://modelcontextprotocol.io). There are several
variations of MCP servers defined by the standard. This module supports:

- Protocol version 2026-07-28 [(see spec)](https://modelcontextprotocol.io/specification/draft)
- Protocol versions 2025-11-25 and 2025-06-18 for older clients [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/changelog#major-changes)
- Stateless MCP servers [(see spec)](https://github.com/modelcontextprotocol/modelcontextprotocol/discussions?discussions_q=stateless)
- Streamable HTTP transport [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http)
- Resources [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)
- Resource templates [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)
- Prompts [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/prompts)
- Tools [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)
- Ping [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/ping)
- Structured content [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/tools#structured-content)
- Progress notifications [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/progress)
- Completions [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/completion)
- Pagination [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/pagination)
- Multi round-trip requests [(see spec)](https://modelcontextprotocol.io/specification/draft/basic/utilities/mrtr)
- Sessions, for the older protocols [(see spec)](https://modelcontextprotocol.io/docs/concepts/transports#session-management)
- Server-sent logging [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/logging)
- List changed events [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization)
- Subscriptions [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization)
- Cancellation [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/cancellation)
- Elicitation [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- Sampling [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/sampling)
- Roots [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/roots)
- MCP Skills (upcoming protocol extension)

This module currently supports these MCP extensions:

- MCP Apps [(see spec)](https://modelcontextprotocol.github.io/ext-apps/api/documents/Overview.html)
- MCP Tasks [(see spec)](https://github.com/modelcontextprotocol/ext-tasks)

## Creating tools, prompts, resources, and completions declaratively

```java
// in some class...

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpDescription;
import io.airlift.mcp.McpPrompt;
import io.airlift.mcp.McpPromptCompletion;
import io.airlift.mcp.McpResource;
import io.airlift.mcp.McpResourceTemplate;
import io.airlift.mcp.McpResourceTemplateCompletion;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.model.ResourceTemplateValues;

@McpTool(name = "add", description = "Adds two numbers")
public int addTwoNumbers(
        @McpDescription("first number to add") int a,
        @McpDescription("second number to add") int b)
{
    return a + b;
}

@McpPrompt(name = "greeting", description = "Generate a greeting message")
public String greeting(@McpDescription("Name of the person to greet") String name)
{
    return "Hello, " + name + "!";
}

@McpPromptCompletion(name = "greeting")
public List<String> nameCompletions(CompleteArgument argument)
{
    if (argument.name().equals("name")) {
        return ImmutableList.of("Jordan", "Rita", "Bobby", "Oliver", "Olive", "Steve")
                .stream()
                .filter(name -> name.toLowerCase().startsWith(argument.value().toLowerCase()))
                .collect(toImmutableList());
    }
    return ImmutableList.of();
}

@McpResource(name = "example1", uri = "file://example1.txt", description = "This is example1 resource.", mimeType = "text/plain")
public ResourceContents resource()
{
    return new ResourceContents("foo2", "file://example1.txt", "text/plain", "This is the content of file://example1.txt");
}

@McpResourceTemplate(name = "example1", uriTemplate = "file:{path1}/{path2}", description = "This is an example resource template", mimeType = "text/plain")
public ResourceContents resourceTemplate(ReadResourceRequest request, ResourceTemplateValues templateValues)
{
    return new ResourceContents("foo2", "file://example1.txt", "text/plain", "This is the content of file://example1.txt");
}

@McpResourceTemplateCompletion(uriTemplate = "file:{path1}/{path2}")
public List<String> resourceTemplateCompletion(CompleteArgument argument)
{
    if (argument.name().equals("id")) {
        return ImmutableList.of("manny", "moe", "jack")
                .stream()
                .filter(uri -> uri.toLowerCase().startsWith(argument.value().toLowerCase()))
                .collect(toImmutableList());
    }
    return ImmutableList.of();
}
```

Add the MCP server Guice module

```java
Module module = McpModule.builder()
    .addAllInClass(MyClassWithToolsPromptsEtc.class)
    .addAllInClass(MyOtherClassWithToolsPromptsEtc.class)
    .withIdentityMapper(YourIdentityType.class, binding -> binding.to(YourIdentityMapper.class).in(SINGLETON))
    .build();

// in your main module, etc.
binder.install(module);
```

## Creating tools, prompts, resources, and completions programmatically

Example of creating a tool programmatically:

- Create a [Tool](src/main/java/io/airlift/mcp/model/Tool.java) instance
- Register the tool with the [McpEntities](src/main/java/io/airlift/mcp/McpEntities.java) (which can be `@Inject`ed):

```java
mcpEntities.addTool(tool, (requestContext, callToolRequest) -> {
    // ... etc ...
    return new CallToolResult(...);
});
```

## Tester/Demo

Run [LocalServer](src/test/java/io/airlift/mcp/LocalServer.java) to showcase an example testing MCP server.

e.g.

```shell
./mvnw -DskipTests install
./mvnw -DskipTests -pl mcp -Dexec.classpathScope=test -Dexec.mainClass=io.airlift.mcp.LocalServer -Dexec.arguments=8888 exec:java
```

In a separate terminal, run the MCP tester:

```shell
npx @modelcontextprotocol/inspector
```

A browser should open with the MCP Inspector tool. Set the "Transport Type" to
"Streamable HTTP". Change the URL to `http://localhost:8888/mcp` and click "Connect".

## Allowed parameters for declarative tools, prompts, resources, and completions

#### Tools

- Parameters can be:
    - `HttpServletRequest`
    - `McpRequestContext`
    - An Identity instance (via [McpIdentityMapper](src/main/java/io/airlift/mcp/McpIdentityMapper.java))
    - [CallToolRequest](src/main/java/io/airlift/mcp/model/CallToolRequest.java)
    - supported Java types
      - `String`
      - `boolean`, `Boolean`
      - `short`, `Short`, `int`, `Integer`, `long`, `Long`
      - `float`, `Float`, `double`, `Double`
      - `BigInteger`, `BigDecimal`
      - `Map<String, String>`
      - Collections of the above types
      - `Optional` of the above types
      - In addition, Java `record`s are supported that use the above types (including
        records of with fields that are records). Note: recursive definitions are not supported.
- Returns either:
    - `void`
    - the supported Java types
    - one of the [Content](src/main/java/io/airlift/mcp/model/Content.java) subtypes
    - [CallToolResult](src/main/java/io/airlift/mcp/model/CallToolResult.java)
    - [StructuredContentResult](src/main/java/io/airlift/mcp/model/StructuredContentResult.java)
    - [ToolResult](src/main/java/io/airlift/mcp/model/ToolResult.java) - a `CallToolResult` or the
      [Task](src/main/java/io/airlift/mcp/model/Task.java) the tool created (see [Tasks](#tasks))

#### Prompts

- Parameters can be:
    - `String` for the prompt arguments
    - `HttpServletRequest`
    - An Identity instance (via [McpIdentityMapper](src/main/java/io/airlift/mcp/McpIdentityMapper.java))
    - [GetPromptRequest](src/main/java/io/airlift/mcp/model/GetPromptRequest.java)
- Returns either:
    - `String`
    - one of the [Content](src/main/java/io/airlift/mcp/model/Content.java) subtypes
    - [GetPromptResult](src/main/java/io/airlift/mcp/model/GetPromptResult.java)

#### Resources

- Parameters can be:
    - `HttpServletRequest`
    - `McpRequestContext`
    - An Identity instance (via [McpIdentityMapper](src/main/java/io/airlift/mcp/McpIdentityMapper.java))
    - [Resource](src/main/java/io/airlift/mcp/model/Resource.java) - the source resource being read
    - [ReadResourceRequest](src/main/java/io/airlift/mcp/model/ReadResourceRequest.java)
- Returns either:
    - `String`
    - [ResourceContents](src/main/java/io/airlift/mcp/model/ResourceContents.java)
    - [List&lt;ResourceContents&gt;](src/main/java/io/airlift/mcp/model/ResourceContents.java)

#### ResourceTemplates

- Parameters can be:
    - `HttpServletRequest`
    - `McpRequestContext`
    - An Identity instance (via [McpIdentityMapper](src/main/java/io/airlift/mcp/McpIdentityMapper.java))
    - [ResourceTemplate](src/main/java/io/airlift/mcp/model/ResourceTemplate.java) - the source resource template being read
    - [ReadResourceRequest](src/main/java/io/airlift/mcp/model/ReadResourceRequest.java)
    - [ResourceTemplateValues](src/main/java/io/airlift/mcp/model/ResourceTemplateValues.java)
- Returns either:
    - `String`
    - [ResourceContents](src/main/java/io/airlift/mcp/model/ResourceContents.java)
    - [List&lt;ResourceContents&gt;](src/main/java/io/airlift/mcp/model/ResourceContents.java)

#### Completions (for prompts and resource templates)

- Parameters can be:
    - `HttpServletRequest`
    - `McpRequestContext`
    - An Identity instance (via [McpIdentityMapper](src/main/java/io/airlift/mcp/McpIdentityMapper.java))
    - [CompleteArgument](src/main/java/io/airlift/mcp/model/CompleteRequest.java)
    - [CompleteContext](src/main/java/io/airlift/mcp/model/CompleteRequest.java)
- Returns either:
    - [CompleteCompletion](src/main/java/io/airlift/mcp/model/CompleteResult.java)
    - `List<String>`

## Sessions

[Sessions](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#session-management) belong to the
2025-11-25 and earlier protocols, where they carry server-sent logging, subscriptions and resumable messages. The
2026-07-28 protocol is stateless and uses none of that, so a server only needs sessions to serve older clients:

```java
McpModule.builder()
    .withStorage(binding -> binding.to(MemoryStorageController.class).in(SINGLETON))
    .withLegacyBindings().withSessions(binding -> binding.to(StandardSessionController.class).in(SINGLETON))
    .build();
```

Sessions are kept in a
[StorageController](src/main/java/io/airlift/mcp/operations/legacy/storage/StorageController.java). For Production, a
DB-backed, resilient implementation should be used; for testing, an in-memory implementation is provided:
[MemoryStorageController](src/main/java/io/airlift/mcp/operations/legacy/storage/MemoryStorageController.java).

## Apps

see: [McpApp](src/main/java/io/airlift/mcp/McpApp.java)

Airlift supports the [MCP Apps extension](https://modelcontextprotocol.github.io/ext-apps/api/documents/Overview.html).
You declare a tool as an MCP App by setting the `app` attribute of the `@McpTool` annotation using `@McpApp`. Airlift will
automatically create the MCP UI resource. You can refer to the same MCP app URI in other tools as long as the
`resourceUri` and `sourcePath` are the same. `sourcePath` refers to the path in your application of the compiled/built
app HTML file. See the examples for more details: [MapApp](src/test/java/io/airlift/mcp/MapApp.java) 
and [DebugApp](src/test/java/io/airlift/mcp/DebugApp.java).

## Skills

The upcoming MCP Skills spec is supported via `@McpSkill` and `@McpSkillTemplate` annotations. These annotations mark
a method as returning MCP Skills. These are generated as normal MCP resources but marked as being Skills so that they
are listed in the skills index and MCP server instructions.

## Tasks

Airlift supports the [MCP Tasks extension](https://github.com/modelcontextprotocol/ext-tasks), which lets a
`tools/call` answer with the handle of a task instead of a result. The client polls `tasks/get` for the status and
the eventual result, answers the task's input requests with `tasks/update`, and stops it with `tasks/cancel`.

Enable tasks by giving the `McpModule` builder an engine:

```java
McpModule.builder()
    .withAllInClass(MyClassWithTools.class)
    .withTasks(binding -> binding.to(MemoryTaskEngine.class).in(SINGLETON))
    .build();
```

[McpTasks](src/main/java/io/airlift/mcp/McpTasks.java) owns the state of tasks: `createTask()` records one without
running it, `getTask()`, `updateTask()` and `cancelTask()` answer the client's `tasks/*` requests, and `executeTask()`
runs - or schedules - the task's [TaskExecutor](src/main/java/io/airlift/mcp/tasks/TaskExecutor.java).
Running a task is always the caller's choice: an engine whose own workers run tasks implements `executeTask()` as a
no-op and they pick the task up from its store instead. A task records the identity that created it and the tool call
it was created for, which is all that is needed to run it - in another process, or after a restart.

[MemoryTaskEngine](src/main/java/io/airlift/mcp/tasks/memory/MemoryTaskEngine.java) keeps tasks in memory and runs
them on virtual threads in this process. For Production, implement `McpTasks` over a store whose tasks survive a
restart and are visible to every instance of the server - the tools themselves do not change when the engine does.

### Tools that create tasks

A tool creates a task for the call it is handling with `createTask()` of the `McpRequestContext`, and returns the
[Task](src/main/java/io/airlift/mcp/model/Task.java) - Airlift answers the `tools/call` with the task handle.
Creating a task does not run it, and `createAndExecuteTask()` does both in one step. Either way the work is a
[TaskExecutor](src/main/java/io/airlift/mcp/tasks/TaskExecutor.java), which returns the tool's `CallToolResult` or
another [TaskResult](src/main/java/io/airlift/mcp/model/TaskResult.java) - `Failed`, `Cancelled` or `Suspended`.
Whatever it throws is turned into a result for it, so it does not have to catch anything:

```java
@McpTool(name = "send_report", description = "Compiles and sends a report", taskSupport = REQUIRED)
public ToolResult sendReport(McpRequestContext requestContext, CallToolRequest callToolRequest, String recipient)
{
    return requestContext.createAndExecuteTask(callToolRequest, (_, _, taskContext) -> sendReport(taskContext, recipient));
}

private CallToolResult sendReport(TaskContext taskContext, String recipient)
        throws InterruptedException
{
    taskContext.setStatusMessage("compiling");
    Report report = compileReport(recipient);

    ObjectNode schema = new JsonSchemaBuilder().build(Confirmation.class);
    ElicitRequestForm form = new ElicitRequestForm("Send %s pages to %s?".formatted(report.pages(), recipient), schema);
    Map<String, Object> inputResponses = taskContext.awaitInputResponses(
            ImmutableMap.of("confirm", new InputRequest(METHOD_ELICITATION_CREATE, form)),
            Duration.ofMinutes(5));

    // ... send the report ...
    return new CallToolResult(new TextContent("Sent"));
}
```

The `taskSupport` attribute of `@McpTool` declares what the server requires of the client before calling the tool:

| `taskSupport` | Behavior                                                                                                  |
|---------------|-----------------------------------------------------------------------------------------------------------|
| `NONE`        | The tool never creates a task. This is the default.                                                       |
| `OPTIONAL`    | The tool may create a task, so it is called either way and decides for itself.                            |
| `REQUIRED`    | The tool always creates a task, so clients that have not negotiated the extension are rejected with `-32021`. |

An `OPTIONAL` tool is called whether or not the client negotiated the extension, so it checks before creating a task:
`requestContext.clientCapabilities().hasExtension(EXTENSION_TASKS)`. A `REQUIRED` tool is only called once that check
has passed.

[TaskContext](src/main/java/io/airlift/mcp/tasks/TaskContext.java) is how the running task reports on itself:

- `setStatusMessage()` sets the message returned with the task's status. The handler is its only writer.
- `requestInput()` moves the task to `input_required` and returns, surfacing the requests on `tasks/get`. An executor
  that does not want to block asks for input, returns `TaskResult.Suspended` - which leaves the task as it is instead
  of settling it - and is run again once `tasks/update` has answered every request, with the answers in
  `inputResponses()`.
- `awaitInputResponses()` does the same and blocks until the client has answered or the timeout elapses.
- `isCancellationRequested()` reports a `tasks/cancel`. Handlers that block are interrupted, so they must be prepared
  to handle an `InterruptedException`; handlers that do not block should poll this and return promptly.

An executor that suspends is run from the top again, so it decides what to do from what has already been answered:

```java
(_, _, taskContext) -> {
    Map<String, Object> inputResponses = taskContext.inputResponses();
    if (inputResponses.isEmpty()) {
        taskContext.requestInput(ImmutableMap.of("confirm", new InputRequest(METHOD_ELICITATION_CREATE, form)));
        return Suspended.INSTANCE;
    }

    // ... the client has answered - finish the work ...
    return new CallToolResult(new TextContent("Sent"));
}
```

A tool is free to create the task at any point - for example only after gathering input over several
[multi round-trip requests](https://modelcontextprotocol.io/specification/draft/basic/utilities/mrtr):

```java
@McpTool(name = "deploy", description = "Deploys a build", taskSupport = REQUIRED)
public ToolResult deploy(McpRequestContext requestContext, CallToolRequest callToolRequest, InputResponses<?> inputResponses)
{
    if (inputResponses.getInputResponse("confirm").isEmpty()) {
        return CallToolResult.inputRequestsBuilder()
                .add("confirm", METHOD_ELICITATION_CREATE, confirmationRequest())
                .build();
    }

    return requestContext.createAndExecuteTask(callToolRequest, (_, _, taskContext) -> deployBuild(taskContext));
}
```

### Status notifications

Clients that do not want to poll can subscribe to `notifications/tasks` by listing task ids in the `notifications` of
a `subscriptions/listen` request. Airlift sends the complete task - exactly what `tasks/get` would have returned - each
time it changes.

### Configuration

| Property                 | Description                                                          | Default |
|--------------------------|----------------------------------------------------------------------|---------|
| `mcp.task.ttl`           | How long a task is retained from creation                            | `15m`   |
| `mcp.task.poll-interval` | The polling interval suggested to clients as `pollIntervalMs`        | `5s`    |

### Security

Tasks are bound to the identity that created them: `tasks/get`, `tasks/update` and `tasks/cancel` report another
caller's task exactly as they report a task that does not exist, and task ids are generated so that they cannot be
guessed. An engine compares identities with `equals()`, so an identity type used with tasks must implement it.

A task outlives the request that created it: while it runs there is no HTTP request and the client is not connected.
A task's work therefore gets only its `TaskContext` - no request context, no progress notifications and no
server-sent logging - and reaches the client by way of `tasks/get` and its input requests.

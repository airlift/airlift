[◀︎ Airlift](../README.md)

# MCP server support

## Introduction

This module provides support for creating [MCP servers](https://modelcontextprotocol.io). There are several
variations of MCP servers defined by the standard. This module supports:

- Protocol version 2025-11-25 [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/changelog#major-changes)
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
- Sessions [(see spec)](https://modelcontextprotocol.io/docs/concepts/transports#session-management)
- Server-sent logging [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/logging)
- List changed events [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization)
- Subscriptions [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization)
- Cancellation [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/cancellation)
- Elicitation [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- Sampling [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/sampling)
- Roots [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/roots)
- Tasks [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/tasks)

This module currently supports these MCP extensions:

- MCP Apps [(see spec)](https://modelcontextprotocol.github.io/ext-apps/api/documents/Overview.html)

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
- Register the tool with the [McpServer](src/main/java/io/airlift/mcp/McpServer.java) (which can be `@Inject`ed):

```java
mcpServer.addTool(tool, (requestContext, callToolRequest) -> {
    // ... etc ...
    return new CallToolResult(...);
});
```

## Tester/Demo

Run [LocalServers](../src/test/java/io/airlift/mcp/LocalServer.java) to showcase an example testing MCP server.

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

Airlift MCP servers can optionally be configured to support MCP [sessions](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#session-management).
Currently, sessions are required for
[server-sent logging](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/logging),
however that will likely change in a future version of the MCP spec.

To enable session support use the `withSessions()` method of the `McpModule`. For Production, a DB-backed,
resilient implementation of [SessionController](src/main/java/io/airlift/mcp/sessions/SessionController.java) should be used. For testing, an in-memory implementation is provided:
[MemorySessionController](src/main/java/io/airlift/mcp/sessions/MemorySessionController.java).

<<<<<<< HEAD
## Apps

see: [McpApp](src/main/java/io/airlift/mcp/McpApp.java)

Airlift supports the [MCP Apps extension](https://modelcontextprotocol.github.io/ext-apps/api/documents/Overview.html).
You declare a tool as an MCP App by setting the `app` attribute of the `@McpTool` annotation using `@McpApp`. Airlift will
automatically create the MCP UI resource. You can refer to the same MCP app URI in other tools as long as the
`resourceUri` and `sourcePath` are the same. `sourcePath` refers to the path in your application of the compiled/built
app HTML file. See the examples for more details: [MapApp](src/test/java/io/airlift/mcp/MapApp.java) 
and [DebugApp](src/test/java/io/airlift/mcp/DebugApp.java).
=======
## Tasks

When [sessions](#sessions) are enabled, Airlift MCP servers can also support MCP [tasks](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/tasks).

### TaskContextId

Tasks are long-running processes that may outlive a given session (if desired). Thus, tasks are tied to a `TaskContextId` rather than a `SessionId`.
If you don't need a higher level of isolation for tasks  you can simply use the current `SessionId` as the `TaskContextId`. All `SessionId`s are, by
definition, valid `TaskContextId`s. Bear in mind, however, that standard sessions have a limited lifetime.

To create a `TaskContextId` use `createTaskContext()` from the [TaskController](src/main/java/io/airlift/mcp/tasks/TaskController.java). 
You must store this `TaskContextId` in a DB, etc. and associate it with the current `McpIdentity` so that it can be returned by your `TaskContextMapper` (see below).
You should also periodically call `TaskController.validateTaskContext()` for any task contexts you create so that task context maintenance
can run.

### TaskContextMapper

Regardless of how you choose to create `TaskContextId`s, you must implement a `TaskContextMapper` that maps from your application's
identity type to a `TaskContextId`s. Bind your `TaskContextMapper` via the `McpModule`. Use `TaskContextMapper.FROM_SESSION` if you want to 
use the current session as the task context (again, standard sessions have a limited lifetime).

### Managing tasks

MCP tools can be declared to support tasks via the `execution` attribute of `McpTool`. To start a task as the result of a tool,
access the [Tasks](src/main/java/io/airlift/mcp/tasks/Tasks.java) instance for the request via `McpRequestContext.tasks()` (`McpRequestContext`
can be an argument to any tool/prompt/etc. method) and use the `createTask()` method to create a task which should be returned as part 
of the `CallToolResult` per the MCP spec. Airlift manages the lifecycle of the task, but it is your application's responsibility to implement 
the actual work of the task. Use the methods of `Tasks` to set server-to-client requests, read responses from the client, etc. Ultimately, 
when the task is complete, call `completeTask` to mark the task as finished.

### Cancellation

Any task related operations should, ideally, be wrapped in `Tasks`'s `executeCancellable()` method so that they can react
to cancellation (via `InterruptedException`). If this isn't possible, you can check for cancellation via polling the task state.
>>>>>>> e09006b44 (General support for MCP tasks)

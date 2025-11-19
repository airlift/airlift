[◀︎ Airlift](../README.md)

# MCP server support

## Introduction

This module provides support for creating [MCP servers](https://modelcontextprotocol.io). There are several
variations of MCP servers defined by the standard. This module supports:

- Protocol version 2025-06-18 [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/changelog#major-changes)
- Stateless MCP servers [(see spec)](https://github.com/modelcontextprotocol/modelcontextprotocol/discussions?discussions_q=stateless)
- Streamable HTTP transport [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#streamable-http)
- Resources [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/resources)
- Resource templates [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/resources)
- Prompts [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/prompts)
- Tools [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/tools)
- Ping [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/ping)
- Structured content [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/tools#structured-content)
- Progress notifications [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress)
- Sessions [(see spec)](https://modelcontextprotocol.io/docs/concepts/transports#session-management)
- Server-sent logging [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging)

It uses the [MCP reference Java SDK](https://github.com/modelcontextprotocol/java-sdk) as its internal implementation.
This implementation is very limited at does not support:

- Completions [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion)
- `_meta` field [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic#meta)
- `context` field [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/changelog) in `CompletionRequest`
- Cancellation [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/cancellation)
- List changed events [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle#initialization)
- Subscriptions [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle#initialization)
- Pagination [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/pagination)
- Elicitation [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/elicitation)
- Roots [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/roots)
- Sampling [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/sampling)

## Creating tools, prompts, and resources declaratively

```java
// in some class...

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

## Creating tools, prompts, and resources programmatically

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

## Allowed parameters for declarative tools, prompts, and resources

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

## Sessions

Airlift MCP servers can optionally be configured to support MCP [sessions](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management). 
Currently, sessions are required for 
[server-sent logging](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging),
however that will likely change in a future version of the MCP spec.

To enable session support use the `withSessions()` method of the `McpModule`. For Production, a DB-backed,
resilient implementation of [SessionController](src/main/java/io/airlift/mcp/sessions/SessionController.java) should be used. For testing, an in-memory implementation is provided:
[MemorySessionController](src/main/java/io/airlift/mcp/sessions/MemorySessionController.java).
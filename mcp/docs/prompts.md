[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - prompts

From the [spec](https://modelcontextprotocol.io/specification/2025-06-18/server/prompts):

> The Model Context Protocol (MCP) provides a standardized way for servers to 
> expose prompt templates to clients. Prompts allow servers to provide 
> structured messages and instructions for interacting with language models. 
> Clients can discover available prompts, retrieve their contents, and provide 
> arguments to customize them.

Prompts can be created declaratively using the `@McpPrompt` annotation or
programmatically.

## Declarative MCP "prompts"

Prompts can be defined using the `@McpPrompt` annotation. The prompt name is either
the `name` attribute of the annotation or the method name. The other attributes
of the annotation are used to describe the prompt. The annotation can be
applied to methods in a class that conform to the following rules:

- Parameters can be:
    - `String` for the prompt arguments
    - `jakarta.ws.rs.core.Request`
    - SessionId ([see doc on sessions](sessions.md))
    - [GetPromptRequest](../src/main/java/io/airlift/mcp/model/GetPromptRequest.java)
    - [McpNotifier](misc.md#notifications-to-clients)
- Returns either:
    - `String`
    - one of the [Content](../src/main/java/io/airlift/mcp/model/Content.java) subtypes
    - [GetPromptResult](../src/main/java/io/airlift/mcp/model/GetPromptResult.java)

[Register](install.md) the method's class with [the McpModule](install.md).

## Programmatic MCP "prompts"

1. Define a `PromptHandler`:

```java
public interface PromptHandler
{
    GetPromptResult getPrompt(Request request, McpNotifier notifier, GetPromptRequest getPromptRequest);
}
```

2. Create a [Prompt](../src/main/java/io/airlift/mcp/model/Prompt.java) instance

3. Register the prompt with the [McpServer](../src/main/java/io/airlift/mcp/McpServer.java) (which can be `@Inject`ed):

```java
mcpServer.prompts().add("name", new PromptEntry(prompt, handler));
```

## Note on cancellation

Use the `cancellationRequested()` method of the `McpNotifier` to check if
the request/operation has been requested to be cancelled.

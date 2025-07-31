[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - completions

From the [spec](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion):

> The Model Context Protocol (MCP) provides a standardized way for servers 
> to offer argument autocompletion suggestions for prompts and resource URIs. 
> This enables rich, IDE-like experiences where users receive contextual 
> suggestions while entering argument values.

Completions can be created declaratively using the `@McpCompletion` annotation or
programmatically.

## Declarative MCP "completions"

Completions can be defined using the `@McpCompletion` annotation. The annotation can be
applied to methods in a class that conform to the following rules:

- Parameters can be:
    - [CompletionRequest](../src/main/java/io/airlift/mcp/model/CompletionRequest.java)
    - `jakarta.ws.rs.core.Request`
    - SessionId ([see doc on sessions](sessions.md))
    - [McpNotifier](misc.md#notifications-to-clients)
    - JAX-RS `@Context` parameters
- Returns either:
    - `List<String>`
  - [Optional&lt;Completion&gt;](../src/main/java/io/airlift/mcp/model/Completion.java)

[Register](install.md) the method's class with [the McpModule](install.md).


## Programmatic MCP "completions"

1. Define a `CompletionHandler`:

```java
public interface CompletionHandler
{
    Optional<Completion> completeCompletion(RequestContext requestContext, McpNotifier notifier, CompletionRequest completionRequest);
}
```

2. Register the completion with the [McpServer](../src/main/java/io/airlift/mcp/McpServer.java) (which can be `@Inject`ed):

```java
mcpServer.completions().add("name", new CompletionEntry("name", handler));
```

## Note on cancellation

Use the `cancellationRequested()` method of the `McpNotifier` to check if
the request/operation has been requested to be cancelled.

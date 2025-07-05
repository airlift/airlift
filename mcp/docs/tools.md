[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - tools

From the [spec](https://modelcontextprotocol.io/specification/2025-06-18/server/tools):

> The Model Context Protocol (MCP) allows servers to expose tools that can be 
> invoked by language models. Tools enable models to interact with external 
> systems, such as querying databases, calling APIs, or performing computations. 
> Each tool is uniquely identified by a name and includes metadata describing 
> its schema.

Tools can be created declaratively using the `@McpTool` annotation or 
programmatically.

## Declarative MCP "tools"

Tools can be defined using the `@McpTool` annotation. The tool name is either
the `name` attribute of the annotation or the method name. The other attributes
of the annotation are used to describe the tool. The annotation can be
applied to methods in a class that conform to the following rules:

- Parameters can be:
  - types that conform to the [supported types](misc.md#supported-types)
  - `jakarta.ws.rs.core.Request`
  - [McpNotifier](misc.md#notifications-to-clients)
  - [CallToolRequest](../src/main/java/io/airlift/mcp/model/CallToolRequest.java)
- Returns either:
  - `void`
  - the [supported types](misc.md#supported-types)
  - one of the [Content](../src/main/java/io/airlift/mcp/model/Content.java) subtypes
  - [CallToolResult](../src/main/java/io/airlift/mcp/model/CallToolResult.java)

[Register](install.md) the method's class with [the McpModule](install.md).

## Programmatic MCP "tools"

1. Define a `ToolHandler`:

```java
public interface ToolHandler
{
    CallToolResult callTool(Request request, McpNotifier notifier, CallToolRequest toolRequest);
}
```

2. Create a [Tool](../src/main/java/io/airlift/mcp/model/Tool.java) instance

3. Register the tool with the [McpServer](../src/main/java/io/airlift/mcp/McpServer.java) (which can be `@Inject`ed):

```java
mcpServer.tools().add("name", new ToolEntry(tool, handler));
```

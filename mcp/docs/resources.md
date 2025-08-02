[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - resources and resource templates

From the [spec](https://modelcontextprotocol.io/specification/2025-06-18/server/resources):

> The Model Context Protocol (MCP) provides a standardized way for servers to 
> expose resources to clients. Resources allow servers to share data that 
> provides context to language models, such as files, database schemas, or 
> application-specific information. Each resource is uniquely identified by 
> a URI.

Resources and resource templates can be created declaratively using `@McpResources` annotations or programmatically.

## Declarative MCP "resources"

Resources and resource templates can be defined using the `@McpResources` annotation. The annotation can be
applied to methods in a class that conform to the following rules:

- Parameters can be:
    - `jakarta.ws.rs.core.Request`
    - SessionId ([see doc on sessions](sessions.md))
    - [McpNotifier](misc.md#notifications-to-clients)
    - JAX-RS `@Context` parameters
- Returns either:
    - [ResourcesEntry](../src/main/java/io/airlift/mcp/handler/ResourcesEntry.java)
    - [ResourceTemplatesEntry](../src/main/java/io/airlift/mcp/handler/ResourceTemplatesEntry.java)

[Register](install.md) the method's class with [the McpModule](install.md).

`ResourcesEntry` and `ResourceTemplatesEntry` contain lists of resources/templates
and the handler to be used to read the contents of the resources/templates.

## Programmatic MCP "resources"

1. Define a handler for resources or resource templates listing and resource reading:

```java
public interface ListResourcesHandler
{
  ResourcesEntry listResources(RequestContext requestContext, McpNotifier notifier);
}

public interface ListResourceTemplatesHandler
{
  ResourceTemplatesEntry listResourceTemplates(RequestContext requestContext, McpNotifier notifier);
}
```

2. Register the handlers with the [McpHandlers](../src/main/java/io/airlift/mcp/McpHandlers.java) (which can be `@Inject`ed):

```java
mcpHandlers.addResource(handler);
mcpHandlers.addResourceTemplate(handler);
```

## Note on cancellation

Use the `cancellationRequested()` method of the `McpNotifier` to check if
the request/operation has been requested to be cancelled.

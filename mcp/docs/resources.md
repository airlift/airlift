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

Resources and resource templates can be defined using the `@McpResource` and
`@McpResourceTemplate` annotations. The annotation can be
applied to methods in a class that conform to the following rules:

- Parameters can be:
    - `jakarta.ws.rs.core.Request`
    - [McpNotifier](misc.md#notifications-to-clients)
    - For resources:
      - [Resource](../src/main/java/io/airlift/mcp/model/Resource.java) - the source resource being read
    - For resource templates:
      - [ResourceTemplate](../src/main/java/io/airlift/mcp/model/ResourceTemplate.java) - the source resource being read
    - [ReadResourceRequest](../src/main/java/io/airlift/mcp/model/ReadResourceRequest.java)
- Returns either:
    - [ResourceContents](../src/main/java/io/airlift/mcp/model/ResourceContents.java)
    - [List&lt;ResourceContents&gt;](../src/main/java/io/airlift/mcp/model/ResourceContents.java)

[Register](install.md) the method's class with [the McpModule](install.md).

## Programmatic MCP "resources"

1. Define a handler for reading resources or resource templates:

```java
import io.airlift.mcp.McpNotifier;

public interface ResourceHandler
{
  List<ResourceContents> readResource(Request request, McpNotifier notifier, Resource sourceResource, ReadResourceRequest readResourceRequest);
}

public interface ResourceTemplateHandler
{
  record PathTemplateValues(Map<String, String> values)
  {
    public PathTemplateValues
    {
      values = ImmutableMap.copyOf(values);
    }
  }

  List<ResourceContents> readResource(Request request, McpNotifier notifier, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, PathTemplateValues pathTemplateValues);
}
```

2. Create a [Resource](../src/main/java/io/airlift/mcp/model/Resource.java) or [ResourceTemplate](../src/main/java/io/airlift/mcp/model/ResourceTemplate.java) instance

3. Register the resource/template with the [McpServer](../src/main/java/io/airlift/mcp/McpServer.java) (which can be `@Inject`ed):

```java
mcpServer.resources().add("name", new ResourceEntry(resource, handler));
mcpServer.resourceTemplates().add("name", new ResourceTemplateEntry(resourceTemplate, handler));
```

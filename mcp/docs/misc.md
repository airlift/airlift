[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - miscellaneous

## Notifications to clients

While processing MCP requests, servers may need to notify clients about progress, etc.
Most MCP methods can add an `McpNotifier` parameter to their method signature. While
processing the request, the server can call methods on this notifier to send
messages to the client:

```java
public interface McpNotifier
{
    void notifyProgress(String message, Optional<Double> progress, Optional<Double> total);

    void sendNotification(String notificationType, Object data);
    
    // ...
}
```

Example:
```java
@McpTool    // or prompt, etc
public String myTool(McpNotifier notifier, ...)
{
    notifier.sendProgress("Starting processing", Optional.of(8.0), Optional.of(55.0));
    
    return "something";
}
```

## Current request

MCP methods can take the current JAX-RS request as a parameter. This allows
servers to access request-specific information, such as headers, etc.

```java
@McpTool    // or prompt, etc
public String myTool(Request request, ...)
{
    ...
}
```

## Supported types

For most method parameters or method return types, the MCP server supports the following types:

- `String`
- `boolean`, `Boolean`
- `short`, `Short`, `int`, `Integer`, `long`, `Long`
- `float`, `Float`, `double`, `Double`
- `BigInteger`, `BigDecimal`
- `Map<String, String>`
- Collections of the above types
- `Optional` of the above types

In addition, Java `record`s are supported that use the above types (including
records of with fields that are records). Note: recursive definitions are not supported.

Your method can also return the type defined by MCP handler. E.g.
tools and prompts can return `Content` or tools can return `CallToolResult`, etc.

## Documentation

Most fields, parameters, etc. can be annotated with `@McpDescription` to provide
documentation for MCP clients.

## JsonSchemaBuilder

Use the `JsonSchemaBuilder` to create JSON schema for your method parameters, return types, etc.

## Tester/Demo

Run [LocalServers](../src/test/java/io/airlift/LocalServer.java) to showcase an example testing MCP server.

e.g.

```shell
./mvnw -DskipTests install
./mvnw -DskipTests -pl mcp -Dexec.classpathScope=test -Dexec.mainClass=io.airlift.LocalServer -Dexec.arguments=8888 exec:java
```

In a separate terminal, run the MCP tester:

```shell
npx @modelcontextprotocol/inspector
```

A browser should open with the MCP Inspector tool. Set the "Transport Type" to
"Streamable HTTP". Change the URL to `http://localhost:8888/mcp` and click "Connect".

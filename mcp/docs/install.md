[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - installation and configuration

## Installation and configuration

- Create an MCP module and install into your application via [McpModule](../src/main/java/io/airlift/mcp/McpModule.java).
- Optionally, change any of the default values:
  - `withBasePath()` to change the default URI path of `mcp`
  - Add classes with MCP annotations via `addAllInClass()`
  - Change the server name, version or instructions via `withServerInfo()`
- Build the module via `build()` and install it into your application

## McpHandlers instance

The [McpHandlers](../src/main/java/io/airlift/mcp/McpHandlers.java)
is available via injection. It can be used to register additional tools, prompts, resources, etc.
or to remove existing ones.

## McpServer instance

The [McpServer](../src/main/java/io/airlift/mcp/McpServer.java)
is available via injection. It contains all the protocol methods for MCP
servers.

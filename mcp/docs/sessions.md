[◀︎ Airlift](../README.md) • [◀︎ MCP server support](../README.md)

# MCP server support - sessions

From the [spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management):

> An MCP “session” consists of logically related interactions between a client and a server, beginning 
> with the initialization phase.

Session support in MCP assumes that the server is stateful. Therefore, session support for MCP
in Airlift is optional. You must bind an instance of [SessionController](../src/main/java/io/airlift/mcp/session/SessionController.java)
to enable session support. Your `SessionController` instance should be backed by a persistent store such as
a database so that session state is persistent and consistent across server restarts or horizontal scaling.

See the [SessionController](../src/main/java/io/airlift/mcp/session/SessionController.java)'s JavaDoc for details on usage.

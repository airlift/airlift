[◀︎ Airlift](../README.md)

# MCP server support

## Introduction

For a quick start, see the [quick start guide](docs/quick-start.md).

This module provides support for creating [MCP servers](https://modelcontextprotocol.io). There are several
variations of MCP servers defined by the standard. This module supports:

- Protocol version 2025-06-18 [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/changelog#major-changes)
- Stateless MCP servers [(see spec)](https://github.com/modelcontextprotocol/modelcontextprotocol/discussions?discussions_q=stateless)
- Streamable HTTP transport [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#streamable-http)
- Resources and resource templates [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/resources)
- Prompts [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/prompts)
- Tools [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/tools)
- Completions [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion)
- Progress notifications [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress)
- Ping [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/ping)
- Limited server-sent notifications during processing (e.g. for progress: [see spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress))
- `_meta` field [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic#meta)
- `context` field [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/changelog) in `CompletionRequest`
- Structured content [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/tools#structured-content)

It does not support:

- Sessions [(see spec)](https://modelcontextprotocol.io/docs/concepts/transports#session-management)
- Cancellation [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/cancellation)
- List changed events [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle#initialization)
- Subscriptions [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle#initialization)
- Server-sent logging [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging)
- Pagination (no benefit) [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/pagination)
- Elicitation [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/elicitation)
- Roots [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/roots)
- Sampling [(see spec)](https://modelcontextprotocol.io/specification/2025-06-18/client/sampling)

Additionally, this module is agnostic regarding authentication and authorization.
It is assumed that authz will be handled by the application adding MCP server
support. However, all prompts, tools, etc. methods can receive the active
HTTP request which should allow most authz implementations.

## Quick start

For a quick start, see the [quick start guide](docs/quick-start.md).

## Details

- [Quick start guide](docs/quick-start.md)
- [Tools](docs/tools.md)
- [Prompts](docs/prompts.md)
- [Resources and resource templates](docs/resources.md)
- [Completions](docs/completions.md)
- [Installation and configuration](docs/install.md)
- [Miscellaneous](docs/misc.md)

## Tester/Demo

Run the [tester/demo](docs/misc.md#testerdemo) to see the MCP server in action.

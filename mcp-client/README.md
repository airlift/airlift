[◀︎ Airlift](../README.md)

# MCP client support

## Introduction

This module provides support for writing [MCP clients](https://modelcontextprotocol.io) — i.e. applications that
call MCP servers. It is the client-side counterpart to the [MCP server support](../mcp/README.md) module and shares
the same model classes (`mcp-model`). This module supports:

- Protocol version 2026-07-28 [(see spec)](https://modelcontextprotocol.io/specification/draft)
- Stateless MCP servers [(see spec)](https://github.com/modelcontextprotocol/modelcontextprotocol/discussions?discussions_q=stateless)
- Streamable HTTP transport [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http)
- Server discovery [(see spec)](https://modelcontextprotocol.io/specification/draft)
- Resources [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)
- Resource templates [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)
- Prompts [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/prompts)
- Tools [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)
- Structured content [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/tools#structured-content)
- Progress notifications [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/progress)
- Completions [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/completion)
- Pagination [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/pagination)
- Server-sent logging [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/logging)
- Subscriptions and list changed events [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization)
- Elicitation [(see spec)](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
- Input requests, i.e. multi-round tool responses [(see spec)](https://modelcontextprotocol.io/specification/draft) —
  see [Input requests](#input-requests)
- Tasks extension [(see spec)](https://github.com/modelcontextprotocol/ext-tasks) — see [Tasks](#tasks)
- The legacy (2025-11-25), session-based protocol — see [Legacy protocol](#legacy-protocol)

## Quick start

Create an [McpClient](src/main/java/io/airlift/mcp/client/McpClient.java) from an Airlift `HttpClient`, then
open an [McpConnection](src/main/java/io/airlift/mcp/client/McpConnection.java) to a server URI:

```java
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.ListToolsResult;

import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpMapper.requireContentString;

try (McpConnection connection = mcpClient(httpClient).connect(URI.create("http://localhost:8888/mcp"))) {
    ListToolsResult tools = connection.listTools();

    CallToolResult result = connection.callTool(new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2)));
    System.out.println(requireContentString(result));
}
```

`McpConnection` is `Closeable` and exposes the server-facing operations:

- `serverDiscover()` — server capabilities, instructions, and supported protocol versions
- `listTools()`, `listPrompts()`, `listResources()`, `listResourceTemplates()` — each has an overload taking an
  `Optional<String>` cursor for [pagination](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/pagination),
  and see [Pagination](#pagination) for walking every page
- `callTool()`, `getPrompt()`, `readResource()`, `completeCompletion()`
- `subscribe()` — see [Subscriptions](#subscriptions)

To send `_meta` with a request, use the `META` connection setting — see [Settings](#settings).

The client and its connections are immutable. Every `withSetting(...)` call returns a new instance that shares
the underlying `HttpClient`, so a per-call variation is cheap:

```java
CallToolResult result = connection.withSetting(McpConnectionSetting.PROGRESS_TOKEN, new ProgressToken("my-token"))
        .callTool(callToolRequest);
```

## Settings

All configuration is expressed as settings rather than builder methods. There are two kinds:

- [McpClientSetting](src/main/java/io/airlift/mcp/client/McpClientSetting.java) — applies to the client and every
  connection it creates. Set via `McpClient.withSetting()`.
- [McpConnectionSetting](src/main/java/io/airlift/mcp/client/McpConnectionSetting.java) — applies to a single
  connection. Set via `McpConnection.withSetting()`, or `McpClient.withDefaultConnectionSetting()` to establish the
  default for all connections created by that client.

Every setting always has a value; the current value can be read back with `setting(...)`.

#### Client settings

| Setting                    | Type                                                                                   | Default                    | Purpose                                                             |
|----------------------------|----------------------------------------------------------------------------------------|----------------------------|---------------------------------------------------------------------|
| `CLIENT_NAME`              | `String`                                                                               | `"MCP Client"`             | Client name reported to the server                                  |
| `CLIENT_VERSION`           | `String`                                                                               | `"1.0.0"`                  | Client version reported to the server                               |
| `MODE`                     | [ClientMode](src/main/java/io/airlift/mcp/client/settings/ClientMode.java)              | `LEGACY_PROTOCOL_OPTIONAL` | Which protocol(s) to speak — see [Legacy protocol](#legacy-protocol) |
| `LOGGING_LEVEL`            | `LoggingLevel`                                                                          | `INFO`                     | Minimum level for server-sent log messages                          |
| `ELICITATION_ENABLED`      | `Boolean`                                                                              | `false`                    | Whether the elicitation client capability is advertised             |
| `EXPERIMENTAL`             | [SettingMap](src/main/java/io/airlift/mcp/client/settings/SettingMap.java)              | empty                      | Extra `experimental` client capabilities                            |
| `EXTENSIONS`               | [SettingMap](src/main/java/io/airlift/mcp/client/settings/SettingMap.java)              | empty                      | Extra client capability extensions                                  |
| `REQUEST_CACHE_FACTORY`    | [RequestCacheFactory](src/main/java/io/airlift/mcp/client/settings/RequestCacheFactory.java) | `StandardRequestCache`     | Builds the cache for each connection — see [Caching](#caching)       |
| `MIN_TASK_SERVICE_PERIOD`  | `Duration`                                                                              | 10 seconds                 | Lower bound on task polling interval — see [Tasks](#tasks)          |
| `MAX_TASK_SERVICE_PERIOD`  | `Duration`                                                                              | 1 minute                   | Upper bound on task polling interval — see [Tasks](#tasks)          |

#### Connection settings

| Setting                      | Type                                                                                                   | Default                   | Purpose                                                                       |
|------------------------------|--------------------------------------------------------------------------------------------------------|---------------------------|-------------------------------------------------------------------------------|
| `NOTIFICATION_CONSUMER`      | [NotificationConsumer](src/main/java/io/airlift/mcp/client/settings/NotificationConsumer.java)         | no-op                     | Receives every notification sent by the server                                |
| `PROGRESS_TOKEN`             | [ProgressToken](src/main/java/io/airlift/mcp/client/settings/ProgressToken.java)                       | empty                     | Progress token sent with requests                                             |
| `REQUEST_FILTER`             | [RequestFilter](src/main/java/io/airlift/mcp/client/settings/RequestFilter.java)                       | identity                  | Mutate outgoing HTTP requests (e.g. add an auth header)                       |
| `RESPONSE_FILTER`            | [ResponseFilter](src/main/java/io/airlift/mcp/client/settings/ResponseFilter.java)                     | identity                  | Observe/rewrite JSON-RPC responses                                            |
| `EXCEPTION_MAPPER`           | [ExceptionMapper](src/main/java/io/airlift/mcp/client/settings/ExceptionMapper.java)                   | `StandardExceptionMapper` | Maps request failures to exceptions — see [Errors](#errors)                   |
| `MAX_INPUT_REQUEST_ROUNDS`   | `Integer`                                                                                              | 10                        | Maximum input request rounds per call — see [Input requests](#input-requests) |
| `LEGACY_ELICITATION_HANDLER` | [LegacyElicitationHandler](src/main/java/io/airlift/mcp/client/settings/LegacyElicitationHandler.java) | rejects                   | Answers server elicitation requests in legacy mode                            |
| `META`                       | [MetaOnly](src/main/java/io/airlift/mcp/client/settings/MetaOnly.java)                                 | empty                     | Default Meta values for requests                                              |

## Notifications, logging, and progress

`NOTIFICATION_CONSUMER` is the single point where server notifications are delivered. It receives every notification
as a raw `(id, method, params)` triple.

[LoggingConsumer](src/main/java/io/airlift/mcp/client/settings/LoggingConsumer.java) and
[ProgressConsumer](src/main/java/io/airlift/mcp/client/settings/ProgressConsumer.java) are narrower views of that
same setting: each extends `NotificationConsumer`, ignores everything but its own notification, and hands it to you
already converted — a `LoggingMessageNotification` or a `ProgressNotification`. So either can be used as the
notification consumer directly, and only the ones you care about need writing:

```java
ProgressConsumer progressConsumer = progressNotification -> System.out.println(progressNotification.message());

McpConnection connection = mcpClient(httpClient)
        .withDefaultConnectionSetting(McpConnectionSetting.NOTIFICATION_CONSUMER, progressConsumer)
        .connect(uri);
```

Use `NotificationConsumer.andThen()` to have more than one of them, in any mixture with a raw consumer:

```java
NotificationConsumer notificationConsumer = (_, method, params) -> System.out.printf("%s: %s%n", method, params);
ProgressConsumer progressConsumer = System.out::println;
LoggingConsumer loggingConsumer = System.out::println;

McpConnection connection = mcpClient(httpClient)
        .withSetting(McpClientSetting.LOGGING_LEVEL, LoggingLevel.DEBUG)
        .withDefaultConnectionSetting(
                McpConnectionSetting.NOTIFICATION_CONSUMER,
                notificationConsumer.andThen(progressConsumer).andThen(loggingConsumer))
        .connect(uri);
```

Consumers can also be layered on for a single call by composing with the current value:

```java
connection.withSetting(
                McpConnectionSetting.NOTIFICATION_CONSUMER,
                connection.setting(McpConnectionSetting.NOTIFICATION_CONSUMER).andThen(loggingConsumer))
        .callTool(callToolRequest);
```

## Errors

Failures surface as one of four exception types, distinguished by where the failure happened:

- [McpException](../mcp-model/src/main/java/io/airlift/mcp/McpException.java) — the server returned a JSON-RPC
  error. `errorDetail()` carries the code, message, and optional data.
- [McpClientException](../mcp-model/src/main/java/io/airlift/mcp/McpClientException.java) — the client could not
  interpret an otherwise successful exchange (e.g. a `McpMapper.require*` helper did not find what was asked for, a
  tool reported an error, or the [input request round limit](#input-requests) was exceeded). It wraps an `McpException`, available via
  `unwrap()`.
- `UnexpectedResponseException` (from `http-client`) — an HTTP-level failure (non-2xx status). It carries the status
  code and response headers.
- `IllegalStateException` / `IllegalArgumentException` — local misuse that never touched the wire, such as calling a
  task operation on a legacy-protocol connection.

Exceptions raised while executing a request pass through the connection's `EXCEPTION_MAPPER`. The default
[StandardExceptionMapper](src/main/java/io/airlift/mcp/client/settings/StandardExceptionMapper.java) maps timeouts to
`REQUEST_TIMEOUT`, illegal state/argument to `INVALID_REQUEST`, and anything unrecognized to `INTERNAL_ERROR`, always
preserving the original exception as the cause. Replace it to customize how failures are surfaced. A replacement
should return an `McpException` unchanged: its cause records what the server answered, which a
`LEGACY_PROTOCOL_OPTIONAL` connection uses to decide whether to fall back:

```java
McpConnection connection = mcpClient(httpClient)
        .withDefaultConnectionSetting(McpConnectionSetting.EXCEPTION_MAPPER, myExceptionMapper)
        .connect(uri);
```

## Subscriptions

`subscribe()` opens a listen stream on a background virtual thread. Notifications are delivered to the
connection's `NOTIFICATION_CONSUMER`. The returned `AutoCloseable` cancels the subscription.

```java
SubscriptionFilter filter = new SubscriptionFilter(Optional.of(true), Optional.of(true), Optional.of(true), Optional.empty());
try (var _ = connection.subscribe(new SubscriptionNotifications(filter, Optional.empty()))) {
    // ... notifications arrive on the notification consumer ...
}
```

The stream's lifetime is bounded by the server: when the server's event streaming timeout elapses (five minutes by
default for an Airlift MCP server — `mcp.event-streaming.timeout`), the stream ends and no further notifications are
delivered. The client does not automatically re-subscribe; re-subscribe when a longer-lived subscription is needed.
A listen stream that fails is logged as an error; cancellation and normal expiry are logged at debug level.

Under the [legacy protocol](#legacy-protocol) the close semantics differ: closing the subscription unsubscribes the
resource subscriptions on the server but does not terminate the session's event stream, so list-changed
notifications may continue to arrive until the server ends the stream.

## Pagination

The `list*()` methods return one page. Passing the `nextCursor()` of a result back into the cursor overload walks
the pages by hand; [McpStreamUtil](src/main/java/io/airlift/mcp/client/McpStreamUtil.java) does it for you, as a
lazy `Stream` that fetches a page at a time:

```java
import static io.airlift.mcp.client.McpStreamUtil.streamTools;

List<String> toolNames = streamTools(connection)
        .map(Tool::name)
        .collect(toImmutableList());
```

`streamPrompts()`, `streamResources()`, and `streamResourceTemplates()` are the same. Nothing is requested until the
stream is consumed, and no page is fetched before it is needed — so `streamTools(connection).findFirst()` costs a
single request. Each page goes through the connection normally, so the [cache](#caching) applies per cursor and, on
a `LEGACY_PROTOCOL_OPTIONAL` connection, a stream started before a protocol fallback continues across it. The stream
is not bounded: a server that keeps returning cursors produces an endless stream, so apply `limit()` if that is a
concern.

## Caching

Results that the server marks as cacheable (tool/prompt/resource/resource-template lists, resource reads, and
server discovery) are served through a cache built for each connection by the `REQUEST_CACHE_FACTORY` setting. The
factory is handed the connection's URI and must return a cache for that endpoint — a cache is bound to the endpoint
it was built for and rejects requests for any other, so an instance must not be shared between connections.

The default [StandardRequestCache](src/main/java/io/airlift/mcp/client/settings/StandardRequestCache.java) is a
Guava-backed cache that honors each result's TTL and cache scope. A result with a TTL of zero (or less) is never
cached — the server is declaring it must not be reused — and nor is a result without a TTL, which the
[spec](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching) has treated as immediately
stale. No result is kept longer than `withMaxTtl()` (15 minutes by default), whatever its TTL. A result is only reused for a request with the same method and parameters, `_meta` included — the
protocol version, the client capabilities and any `META` a connection adds can each change what the server returns.
Connections derived with `withSetting()` share a cache, so this is also what keeps one connection's results from
another's. The progress token and log level are left out, as they describe the call rather than what it asks for.
Paginated list results are cached per cursor; if fetching a later page fails — its cursor may no longer be valid —
every cached page of that listing is discarded, so the next listing starts again from its first page. Input-required
results (see
[Input requests](#input-requests)) are never cached, as they are per-caller conversation state. Nor is the result of
a retry that answers them: a request carrying input responses or a request state depends on inputs that are not part
of the cache key, so it bypasses the cache — whichever `RequestCache` is installed — and its result is neither served
from nor stored in it.

`PUBLIC` results — including results whose scope the server left unset — are shared across callers. Any other result
belongs to one caller, identified by the `authMapper`, which reads the `Authorization` header by default. **A
deployment that authenticates any other way must supply its own mapper**: when the caller cannot be identified the
mapper returns a blank value, and a caller-scoped result is then not cached at all rather than being stored under a
key every caller shares.

The cache also purges itself when the server says its data has changed, so entries do not have to age out to become
correct: `notifications/tools/list_changed` and `notifications/prompts/list_changed` drop the corresponding listing,
`notifications/resources/list_changed` drops both the resource and resource-template listings, and
`notifications/resources/updated` drops every read of just the resource it names. A list purge covers every cached
cursor of that listing. Note that these notifications only arrive while a [subscription](#subscriptions) is open.

```java
McpClient client = mcpClient(httpClient)
        .withSetting(McpClientSetting.REQUEST_CACHE_FACTORY, uri -> StandardRequestCache.builder(uri)
                .withMaxEntries(5_000)
                .withMaxTtl(Duration.ofMinutes(5))
                .withAuthMapper(request -> request.getHeader(HeaderName.of("X-My-Identity")))
                .build());
```

Replace the factory with one returning your own
[RequestCache](src/main/java/io/airlift/mcp/client/settings/RequestCache.java) to cache differently. `RequestCache`
extends [NotificationConsumer](src/main/java/io/airlift/mcp/client/settings/NotificationConsumer.java) so an
implementation can observe the notifications above; the default implementation of `accept()` does nothing, so a
custom cache only expires on TTL unless it overrides it.

## Input requests

A tool call, prompt get, or resource read can come back asking the client for more input instead of a final result —
[the multi-round response](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/mrtr) flow. `CallToolResult`, `GetPromptResult`, and `ReadResourceResult` implement
[InputRequests](../mcp-model/src/main/java/io/airlift/mcp/model/InputRequests.java): a `requestState()` token and,
when more input is required, an `inputRequests()` map of server-chosen keys to
[InputRequest](../mcp-model/src/main/java/io/airlift/mcp/model/InputRequest.java) — each naming a method (e.g.
`elicitation/create`) and its params. The corresponding requests (`CallToolRequest`, `GetPromptRequest`,
`ReadResourceRequest`) implement
[InputResponses](../mcp-model/src/main/java/io/airlift/mcp/model/InputResponses.java), so the same request is
re-sent via `withInputResponses()` with the answers keyed the same way.

Implement [McpInputRequestsHandler](src/main/java/io/airlift/mcp/client/McpInputRequestsHandler.java) to answer one
round of requests. Its `asResultProcessor()` adapter drives the round trips until the server returns a final result:

```java
McpInputRequestsHandler inputRequestsHandler = inputRequests -> inputRequests.entrySet()
        .stream()
        .collect(toImmutableMap(
                Map.Entry::getKey,
                _ -> new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("firstName", "John")), Optional.empty())));

String result = inputRequestsHandler.asResultProcessor()
        .process(connection, new CallToolRequest("elicitation", ImmutableMap.of()), McpConnection::callTool, McpMapper::optionalContentString)
        .required();
```

`process()` takes the connection, the initial request, the connection method to invoke, and a mapper from the final
result to a value. It returns a `ProcessorResult`: call `optional()` for the mapped `Optional`, or `required()` to
unwrap it (throwing if the mapper produced nothing).

Two behaviors of the processor to know about:

- It installs the handler's `asLegacyElicitationHandler()` on the connection it uses, so one handler covers servers
  that only speak the legacy protocol — there, elicitation arrives as a server-to-client request rather than as an
  input request, and it is adapted to a single-entry `inputRequests` map. See [Legacy protocol](#legacy-protocol).
- The `MAX_INPUT_REQUEST_ROUNDS` connection setting bounds how many rounds are driven. A server that never converges
  and keeps asking for input causes a `McpClientException` ("Too many input request rounds") rather than an infinite
  loop. The default allows 10 rounds; tune it per client, connection, or call with `withSetting()`.

For tasks that ask for input, use `asTaskResultProcessor()` instead — see [Tasks](#tasks).

## Tasks

The [MCP Tasks extension](https://github.com/modelcontextprotocol/ext-tasks) lets a tool call return a long-running
task instead of an immediate result. Use `withTasks()` to get a
[McpTasksClient](src/main/java/io/airlift/mcp/client/McpTasksClient.java), whose `connect()` returns an
[McpTasksConnection](src/main/java/io/airlift/mcp/client/McpTasksConnection.java); this advertises the tasks
extension to the server and adds the task operations:

- `callToolOrTask()` — returns a `ToolResult`, which is either a `CallToolResult` (the tool completed immediately)
  or a `Task`
- `getTask()` — poll a task's current state
- `updateTask()` — send input responses to a task awaiting input
- `cancelTask()` — ask the server to cancel a task
- `sleepTask(task)` — sleep for the task's poll interval (see pacing, below)

Everything runs on the caller's thread — there are no background pollers. The
`McpInputRequestsHandler.asTaskResultProcessor()` adapter drives a task to completion: it polls while the task is
`WORKING`, answers `INPUT_REQUIRED` rounds through the handler, maps the result of a `COMPLETED` task, throws an
`McpException` carrying the server's error detail for a `FAILED` task, and throws one saying the task was cancelled
for a `CANCELLED` task. If the tool completed immediately, the mapper is applied directly — the same call covers both
outcomes:

```java
McpTasksConnection connection = mcpClient(httpClient).withTasks().connect(uri);

McpInputRequestsHandler inputRequestsHandler = inputRequests -> inputRequests.keySet()
        .stream()
        .collect(toImmutableMap(identity(), _ -> new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("confirm", true)), Optional.empty())));

ToolResult toolResult = connection.callToolOrTask(new CallToolRequest("confirm_delete", ImmutableMap.of("filename", "hey")));
String result = inputRequestsHandler.asTaskResultProcessor()
        .process(connection, toolResult, McpMapper::optionalContentString)
        .required();
```

A server may acknowledge an answer before polling reflects it, so a poll can still show requests the handler has
already answered. As request keys are never reused within a task, the adapter only passes the handler requests it has
not answered yet: a poll showing nothing new is treated as more waiting, and only rounds with new requests count
towards `MAX_INPUT_REQUEST_ROUNDS`.

Notes:

- **Pacing** — between polls the processor calls `sleepTask()`, which sleeps for the task's own `pollIntervalMs`
  clamped into the `MIN_TASK_SERVICE_PERIOD`/`MAX_TASK_SERVICE_PERIOD` band (a task with no suggested interval is
  polled at the minimum period).
- **Blocking and interruption** — `process()` blocks until the task is terminal and declares
  `InterruptedException`; interrupt the thread to abandon the wait. The task keeps running server-side unless
  cancelled.
- **Cancellation** — call `cancelTask(taskId)` (from any thread); the next poll observes the `CANCELLED` status and
  the processor throws with the server's error detail.
- **Input rounds are bounded** — the `MAX_INPUT_REQUEST_ROUNDS` guard from [Input requests](#input-requests) applies
  to `INPUT_REQUIRED` rounds; `WORKING` polls are not bounded, so a slow task may be polled indefinitely.
- Task support requires the current protocol — connecting via `withTasks()` is rejected under `LEGACY_PROTOCOL_ONLY`.

## Legacy protocol

The 2026-07-28 protocol is stateless: there is no `initialize` handshake and no session id. Servers that only
support the older, session-based 2025-11-25 protocol are handled by the `MODE` client setting:

- `LEGACY_PROTOCOL_OPTIONAL` (default) — start with the current protocol and, if the first request fails in a way
  that indicates the server does not speak it, transparently reconnect using the legacy protocol and retry that
  request. The signals are a JSON-RPC `UNSUPPORTED_PROTOCOL` error whose `supported` versions include the legacy
  protocol (or that names no versions at all), or, as the
  [spec](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http#backward-compatibility)
  describes, an HTTP 4xx response other than 401, 403, 408 or 429 — the server rejected the request without acting
  on it, so it is safe to send again. Any other failure of the first request propagates without a retry. A JSON-RPC
  error the server answered with, such as a tool error, settles the current protocol. A failure that does not show
  which protocol the server speaks — a server error, an auth failure, a dropped connection, or a response that
  could not be read — leaves the protocol undecided, so the next request probes again. Requests made while it is
  undecided do not wait for one another: each goes out on the current protocol, and the first to settle the protocol
  decides it — those the server rejected are then sent again on the legacy protocol. Once the protocol is settled a
  later failure is just a failure, and is never retried
- `LEGACY_PROTOCOL_DISABLED` — current protocol only
- `LEGACY_PROTOCOL_ONLY` — legacy protocol only (`initialize` handshake plus session id header). Task support is not
  available in this mode

A legacy server may end a session at any time — on a restart, say — and answers requests in it with a 404. As
[the spec](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#session-management) requires,
the connection then starts a new session with a fresh `initialize` and sends the request once more; every connection
derived from it with `withSetting()` moves to the new session too. Anything the server held for the old session is
lost: a subscription opened in it ends and has to be opened again.

In legacy mode the server drives elicitation as a request to the client rather than as an input request on the
result. This difference does not need to be handled: drive the call through the handler's `asResultProcessor()` and
it installs the handler's `asLegacyElicitationHandler()` on the connection for you, so the same
[McpInputRequestsHandler](src/main/java/io/airlift/mcp/client/McpInputRequestsHandler.java) works against either
protocol — see [Input requests](#input-requests).

```java
McpInputRequestsHandler inputRequestsHandler = inputRequests -> inputRequests.entrySet()
        .stream()
        .collect(toImmutableMap(
                Map.Entry::getKey,
                _ -> new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("firstName", "John")), Optional.empty())));

McpConnection connection = mcpClient(httpClient)
        .withSetting(McpClientSetting.MODE, ClientMode.LEGACY_PROTOCOL_ONLY)
        .connect(uri);

String result = inputRequestsHandler.asResultProcessor()
        .process(connection, new CallToolRequest("elicitation", ImmutableMap.of()), McpConnection::callTool, McpMapper::optionalContentString)
        .required();
```

A legacy elicitation reaches the handler as a single-entry map — key `request`, method `elicitation/create` — and the
handler returns the `ElicitResult` under that same key.

Setting `LEGACY_ELICITATION_HANDLER` yourself is only necessary if legacy elicitation has to be answered outside a
processor-driven call, or if legacy mode needs different answers than the input-requests handler gives. The default
handler rejects all elicitation requests:

```java
LegacyElicitationHandler legacyElicitationHandler = _ ->
        new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("firstName", "John")), Optional.empty());

McpConnection connection = mcpClient(httpClient)
        .withSetting(McpClientSetting.MODE, ClientMode.LEGACY_PROTOCOL_ONLY)
        .withDefaultConnectionSetting(McpConnectionSetting.LEGACY_ELICITATION_HANDLER, legacyElicitationHandler)
        .connect(uri);
```

Under the current protocol the server never calls back to the client: elicitation is surfaced either as an
`INPUT_REQUIRED` result on the call itself (see [Input requests](#input-requests)) or, for a long-running tool, as a
task with status `INPUT_REQUIRED` (see [Tasks](#tasks)).

## Reading results

[McpMapper](src/main/java/io/airlift/mcp/client/McpMapper.java) holds the JSON mapper configured with the MCP
subtypes, along with helpers for pulling values out of results. The `require*` forms throw a
[McpClientException](../mcp-model/src/main/java/io/airlift/mcp/McpClientException.java) when the value is absent;
the `optional*` forms return an `Optional` (and suit the processor mapper parameter as method references). A value
that is present but cannot be read as asked — content that is not text, or structured content of another shape —
throws a `McpClientException` from either form. So does a tool that failed: a tool reports its failure as a result
with `isError` set rather than as a JSON-RPC error, and the content helpers will not read its error text as if it
were the tool's output. The exception's message is the tool's error text, and its error data is the whole
`CallToolResult` — read the result directly to inspect a failure without an exception:

- `requireCallToolResult(toolResult)` — narrow a `ToolResult` to a `CallToolResult`
- `requireContentString(callToolResult)` / `optionalContentString(callToolResult)` — the first content block as text
- `requireStructuredContent(callToolResult, MyRecord.class)` / `optionalStructuredContent(...)` — deserialize
  [structured content](https://modelcontextprotocol.io/specification/2025-11-25/server/tools#structured-content)
- `requireInputRequests(toolResult)` — the input requests of a task awaiting input
- `requireLoggingMessageNotification(params)`, `requireProgressNotification(params)` — convert raw notification
  params into their typed form. The `optional*` forms of these are what `LoggingConsumer`/`ProgressConsumer` use
  internally: a notification the client cannot read is skipped rather than thrown out of, since throwing on the
  thread reading the notification stream would end the subscription
- `jsonMapper()` — the underlying mapper, for anything else

## Tester/Demo

Run the example MCP server from the [mcp](../mcp/README.md) module and point a client at it:

```shell
./mvnw -DskipTests install
./mvnw -DskipTests -pl mcp -Dexec.classpathScope=test -Dexec.mainClass=io.airlift.mcp.LocalServer -Dexec.arguments=8888 exec:java
```

The server is then reachable at `http://localhost:8888/mcp`.

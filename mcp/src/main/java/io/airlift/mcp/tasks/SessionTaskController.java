package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskMetadata;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionKey;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static io.airlift.mcp.tasks.CombinedIds.combineIds;
import static io.airlift.mcp.tasks.CombinedIds.splitIds;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static java.util.Objects.requireNonNull;

public class SessionTaskController
        implements TaskController
{
    private final SessionController sessionController;

    public record ServerToClientRequests(List<JsonRpcRequest<?>> requests)
    {
        public ServerToClientRequests
        {
            requireNonNull(requests, "requests is null");   // don't copy
        }
    }

    public record ServerToClientResponses(Map<UUID, Optional<JsonRpcResponse<?>>> responses)
    {
        public ServerToClientResponses
        {
            requireNonNull(responses, "responses is null");   // don't copy
        }
    }

    @Inject
    public SessionTaskController(SessionController sessionController)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @Override
    public Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        SessionId sessionId = requireSessionId(requestContext.request());
        String rawKeyName = UUID.randomUUID().toString();
        SessionKey<Task> taskKey = SessionKey.of(rawKeyName, Task.class);
        TaskId taskId = sessionIdAndKeyToTaskId(sessionId, rawKeyName);

        OptionalInt ttl = callToolRequest.task().map(TaskMetadata::ttl).orElse(OptionalInt.empty());
        Task task = new Task(taskId.id(), Instant.now(), OptionalInt.empty(), TaskStatus.WORKING, Optional.empty(), ttl);

        if (!sessionController.setSessionValue(sessionId, taskKey, task)) {
            throw exception("Failed to create task for session: %s".formatted(sessionId.id()));
        }

        return task;
    }

    @Override
    public Optional<Task> task(TaskId taskId)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<Task> taskKey = SessionKey.of(sessionIdAndKey.keyName, Task.class);

        return sessionController.getSessionValue(sessionIdAndKey.sessionId, taskKey);
    }

    @Override
    public boolean setTaskStatus(TaskId taskId, TaskStatus status, Optional<String> statusMessage)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<Task> taskKey = SessionKey.of(sessionIdAndKey.keyName, Task.class);

        return sessionController.computeSessionValue(sessionIdAndKey.sessionId, taskKey, maybeTask -> {
            Task currentTask = maybeTask.orElseThrow(() -> exception("Failed to update task: %s".formatted(taskId.id())));
            return Optional.of(currentTask.withStatus(status, statusMessage));
        });
    }

    @Override
    public boolean setTaskResult(TaskId taskId, Optional<CallToolResult> result)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<CallToolResult> callToolResultKey = SessionKey.of(sessionIdAndKey.keyName, CallToolResult.class);

        return result.map(callToolResult -> sessionController.setSessionValue(sessionIdAndKey.sessionId, callToolResultKey, callToolResult))
                .orElseGet(() -> sessionController.deleteSessionValue(sessionIdAndKey.sessionId, callToolResultKey));
    }

    @Override
    public Optional<CallToolResult> getTaskResult(TaskId taskId)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<CallToolResult> callToolResultKey = SessionKey.of(sessionIdAndKey.keyName, CallToolResult.class);

        return sessionController.getSessionValue(sessionIdAndKey.sessionId, callToolResultKey);
    }

    @Override
    public void deleteTask(TaskId taskId)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<Task> taskKey = SessionKey.of(sessionIdAndKey.keyName, Task.class);
        SessionKey<ServerToClientRequests> serverToClientRequestKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientRequests.class);
        SessionKey<ServerToClientResponses> serverToClientResponseKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientResponses.class);
        SessionKey<CallToolResult> callToolResultKey = SessionKey.of(sessionIdAndKey.keyName, CallToolResult.class);

        sessionController.deleteSessionValues(sessionIdAndKey.sessionId, ImmutableList.of(taskKey, serverToClientRequestKey, serverToClientResponseKey, callToolResultKey));
    }

    @Override
    public <T> boolean queueServerToClientMessage(TaskId taskId, Optional<UUID> maybeRequestId, String method, Optional<T> params)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<ServerToClientRequests> serverToClientRequestKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientRequests.class);
        SessionKey<ServerToClientResponses> serverToClientResponseKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientResponses.class);

        maybeRequestId.ifPresent(requestId -> sessionController.computeSessionValue(sessionIdAndKey.sessionId, serverToClientResponseKey, maybeServerToClientResponses -> {
            ServerToClientResponses serverToClientReqsponses = maybeServerToClientResponses.orElseGet(() -> new ServerToClientResponses(new ConcurrentHashMap<>()));
            serverToClientReqsponses.responses.put(requestId, Optional.empty());
            return Optional.of(serverToClientReqsponses);
        }));

        JsonRpcRequest<T> request = new JsonRpcRequest<>(JSON_RPC_VERSION, maybeRequestId.orElse(null), method, params);
        return sessionController.computeSessionValue(sessionIdAndKey.sessionId, serverToClientRequestKey, maybeServerToClientRequests -> {
            ServerToClientRequests serverToClientRequests = maybeServerToClientRequests.orElseGet(() -> new ServerToClientRequests(new CopyOnWriteArrayList<>()));
            serverToClientRequests.requests.add(request);
            return Optional.of(serverToClientRequests);
        });
    }

    @Override
    public void takeServerToClientMessages(TaskId taskId, Consumer<JsonRpcRequest<?>> messageConsumer)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<ServerToClientRequests> serverToClientRequestKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientRequests.class);

        sessionController.computeSessionValue(sessionIdAndKey.sessionId, serverToClientRequestKey, maybeServerToClientRequests -> {
            maybeServerToClientRequests.ifPresent(serverToClientRequests -> {
                while (!serverToClientRequests.requests.isEmpty()) {
                    JsonRpcRequest<?> request = serverToClientRequests.requests.getFirst();
                    messageConsumer.accept(request);
                    serverToClientRequests.requests.removeFirst();
                }
            });
            return maybeServerToClientRequests;
        });
    }

    @Override
    public <T> boolean setServerClientResponse(TaskId taskId, UUID requestId, Optional<T> result, Optional<JsonRpcErrorDetail> error)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<ServerToClientResponses> serverToClientResponseKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientResponses.class);

        AtomicBoolean wasFound = new AtomicBoolean();
        sessionController.computeSessionValue(sessionIdAndKey.sessionId, serverToClientResponseKey, maybeServerToClientResponses -> {
            maybeServerToClientResponses.ifPresent(serverToClientResponses -> serverToClientResponses.responses.computeIfPresent(requestId, (_, _) -> {
                wasFound.set(true);
                return Optional.of(new JsonRpcResponse<>(JSON_RPC_VERSION, requestId.toString(), error, result));
            }));
            return maybeServerToClientResponses;
        });

        return wasFound.get();
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public boolean takeServerToClientResponse(TaskId taskId, UUID requestId, Consumer<JsonRpcResponse<?>> responseConsumer)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<ServerToClientResponses> serverToClientResponseKey = SessionKey.of(sessionIdAndKey.keyName, ServerToClientResponses.class);

        var wasFoundHolder = new Object()
        {
            volatile boolean wasFound;
        };
        sessionController.computeSessionValue(sessionIdAndKey.sessionId, serverToClientResponseKey, maybeServerToClientResponses -> {
            maybeServerToClientResponses.map(serverToClientResponses -> {
                serverToClientResponses.responses.compute(requestId, (_, maybeResponse) -> {
                    if (maybeResponse != null) {
                        if (maybeResponse.isPresent()) {
                            responseConsumer.accept(maybeResponse.get());
                            wasFoundHolder.wasFound = true;
                        }
                        else {
                            // response not yet set
                            return maybeResponse;
                        }
                    }

                    // null is the signal to remove the entry for the JDK's maps
                    return null;
                });
                return maybeServerToClientResponses;
            });
            return maybeServerToClientResponses;
        });

        return wasFoundHolder.wasFound;
    }

    static SessionId requireSessionId(HttpServletRequest request)
    {
        String sessionId = Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .orElseThrow(() -> exception("Missing MCP_SESSION_ID header in request"));
        return new SessionId(sessionId);
    }

    private record SessionIdAndKey(SessionId sessionId, String keyName) {}

    private static TaskId sessionIdAndKeyToTaskId(SessionId sessionId, String keyName)
    {
        return new TaskId(combineIds(sessionId.id(), keyName));
    }

    private static SessionIdAndKey sessionIdAndKeyFromTaskId(TaskId taskId)
    {
        CombinedIds<SessionId, String> combinedIds = splitIds(taskId.id(), SessionId::new, String::valueOf);
        return new SessionIdAndKey(combinedIds.a(), combinedIds.b());
    }
}

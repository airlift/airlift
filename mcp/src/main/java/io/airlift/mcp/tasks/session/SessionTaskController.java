package io.airlift.mcp.tasks.session;

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
import io.airlift.mcp.tasks.CombinedIds;
import io.airlift.mcp.tasks.EndTaskReason;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskId;
import io.airlift.mcp.tasks.TaskResult;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static io.airlift.mcp.tasks.CombinedIds.combineIds;
import static io.airlift.mcp.tasks.CombinedIds.splitIds;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static java.util.Objects.requireNonNull;

// note: tasks, requests and responses are separate Session values
// this is because tasks, requests and responses can be modified while
// modifying each other. So, they are separate to avoid deadlocks
// and recursive modifications
public class SessionTaskController
        implements TaskController
{
    private final SessionController sessionController;

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
        TaskId taskId = sessionIdAndKeyToTaskId(sessionId, rawKeyName);

        OptionalInt ttl = callToolRequest.task().map(TaskMetadata::ttl).orElse(OptionalInt.empty());
        Task task = new Task(taskId.id(), Instant.now(), OptionalInt.empty(), ttl);

        SessionKey<SessionTask> sessionTaskKey = SessionKey.of(rawKeyName, SessionTask.class);
        SessionTask sessionTask = new SessionTask(ttl);
        if (!sessionController.setSessionValue(sessionId, sessionTaskKey, sessionTask)) {
            throw exception("Failed to create task for session: %s".formatted(sessionId.id()));
        }

        return task;
    }

    @Override
    public Optional<TaskStatus> taskStatus(TaskId taskId)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTask> sessionTaskKey = SessionKey.of(sessionIdAndKey.keyName, SessionTask.class);

        // task status is calculated dynamically based on whether there are pending results or responses
        // or whether the task has ended

        return sessionController.getSessionValue(sessionIdAndKey.sessionId, sessionTaskKey)
                .map(sessionTask -> sessionTask.result()
                        .map(result -> switch (result.endTaskReason()) {
                            case COMPLETED -> TaskStatus.COMPLETED;
                            case FAILED -> TaskStatus.FAILED;
                            case CANCELLED -> TaskStatus.CANCELLED;
                        }).or(() -> {
                            SessionKey<SessionTaskResponses> sessionResponsesKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskResponses.class);
                            return sessionController.getSessionValue(sessionIdAndKey.sessionId, sessionResponsesKey)
                                    .map(sessionTaskResponses -> sessionTaskResponses.responses().isEmpty() ? TaskStatus.WORKING : TaskStatus.INPUT_REQUIRED);
                        }).orElse(TaskStatus.WORKING));
    }

    @Override
    public boolean endTask(TaskId taskId, CallToolResult result, EndTaskReason reason, Optional<String> statusMessage)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTask> sessionTaskKey = SessionKey.of(sessionIdAndKey.keyName, SessionTask.class);

        return sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionTaskKey, maybeSessionTask -> {
            SessionTask currentSessionTask = maybeSessionTask.orElseThrow(() -> exception("Failed to set task result: %s".formatted(taskId.id())));
            return Optional.of(currentSessionTask.withResult(new TaskResult(result, reason, statusMessage, Instant.now())));
        });
    }

    @Override
    public Optional<TaskResult> getTaskResult(TaskId taskId)
    {
        return internalTaskResult(taskId, false);
    }

    @Override
    public Optional<TaskResult> finalizeTask(TaskId taskId)
    {
        return internalTaskResult(taskId, true);
    }

    @Override
    public <T> boolean queueServerToClientMessage(TaskId taskId, Optional<UUID> maybeRequestId, String method, Optional<T> params)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);

        JsonRpcRequest<T> request = new JsonRpcRequest<>(JSON_RPC_VERSION, maybeRequestId.orElse(null), method, params);
        SessionKey<SessionTaskRequests> sessionRequestsKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskRequests.class);

        // add the request to the task's SessionTaskRequests list
        boolean found = sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionRequestsKey, maybeSessionRequests -> {
            SessionTaskRequests sessionTaskRequests = maybeSessionRequests.orElseGet(SessionTaskRequests::new);
            sessionTaskRequests.requests().add(request);
            return Optional.of(sessionTaskRequests);
        });

        if (found) {
            found = maybeRequestId.map(requestId -> {
                SessionKey<SessionTaskResponses> sessionResponsesKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskResponses.class);

                // this is a request and not a notification. Add a placeholder for the response
                return sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionResponsesKey, maybeSessionResponses -> {
                    SessionTaskResponses sessionTaskResponses = maybeSessionResponses.orElseGet(SessionTaskResponses::new);

                    sessionTaskResponses.responses().put(requestId, Optional.empty());

                    return Optional.of(sessionTaskResponses);
                });
            }).orElse(false);
        }

        return found;
    }

    @Override
    public void takeServerToClientMessages(TaskId taskId, Consumer<JsonRpcRequest<?>> messageConsumer)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTaskRequests> sessionRequestsKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskRequests.class);

        sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionRequestsKey, maybeSessionRequests -> {
            maybeSessionRequests.ifPresent(currentSessionRequests -> {
                while (!currentSessionRequests.requests().isEmpty()) {
                    JsonRpcRequest<?> request = currentSessionRequests.requests().getFirst();

                    messageConsumer.accept(request);

                    currentSessionRequests.requests().removeFirst();
                }
            });
            return maybeSessionRequests;
        });
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public <T> boolean setServerClientResponse(TaskId taskId, UUID requestId, Optional<T> result, Optional<JsonRpcErrorDetail> error)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTaskResponses> sessionResponsesKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskResponses.class);

        var wasFoundHolder = new Object()
        {
            volatile boolean wasFound;
        };

        sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionResponsesKey, maybeSessionResponses -> {
            maybeSessionResponses.ifPresent(currentSessionResponses -> {
                if (currentSessionResponses.responses().computeIfPresent(requestId, (_, _) -> Optional.of(new JsonRpcResponse<>(JSON_RPC_VERSION, requestId.toString(), error, result))) != null) {
                    wasFoundHolder.wasFound = true;
                }
            });
            return maybeSessionResponses;
        });

        return wasFoundHolder.wasFound;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public boolean takeServerToClientResponse(TaskId taskId, UUID requestId, Consumer<JsonRpcResponse<?>> responseConsumer)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTaskResponses> sessionResponsesKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskResponses.class);

        var wasFoundHolder = new Object()
        {
            volatile boolean wasFound;
        };

        sessionController.computeSessionValue(sessionIdAndKey.sessionId, sessionResponsesKey, maybeSessionResponses -> {
            maybeSessionResponses.ifPresent(currentSessionResponses -> currentSessionResponses.responses().computeIfPresent(requestId, (_, maybeRpcResponse) -> {
                if (maybeRpcResponse.isPresent()) {
                    responseConsumer.accept(maybeRpcResponse.get());

                    wasFoundHolder.wasFound = true;
                    // remove the response after it has been consumed
                    return null;
                }

                // response not yet set
                return maybeRpcResponse;
            }));
            return maybeSessionResponses;
        });

        return wasFoundHolder.wasFound;
    }

    private Optional<TaskResult> internalTaskResult(TaskId taskId, boolean finalize)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTask> sessionTaskKey = SessionKey.of(sessionIdAndKey.keyName, SessionTask.class);

        return sessionController.getSessionValue(sessionIdAndKey.sessionId, sessionTaskKey)
                .flatMap(sessionTaskResult -> {
                    Optional<TaskResult> maybeResult = sessionTaskResult.result();
                    if (finalize) {
                        maybeResult.ifPresent(_ -> deleteTaskFromSession(sessionIdAndKey.sessionId, taskId));
                    }
                    return maybeResult;
                });
    }

    private void deleteTaskFromSession(SessionId sessionId, TaskId taskId)
    {
        SessionIdAndKey sessionIdAndKey = sessionIdAndKeyFromTaskId(taskId);
        SessionKey<SessionTask> sessionTaskKey = SessionKey.of(sessionIdAndKey.keyName, SessionTask.class);
        SessionKey<SessionTaskResponses> sessionResponsesKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskResponses.class);
        SessionKey<SessionTaskRequests> sessionRequestsKey = SessionKey.of(sessionIdAndKey.keyName, SessionTaskRequests.class);

        sessionController.deleteSessionValue(sessionId, sessionTaskKey);
        sessionController.deleteSessionValue(sessionId, sessionResponsesKey);
        sessionController.deleteSessionValue(sessionId, sessionRequestsKey);
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

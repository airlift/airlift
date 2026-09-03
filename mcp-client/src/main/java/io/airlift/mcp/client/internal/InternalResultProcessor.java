package io.airlift.mcp.client.internal;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.McpInputRequestsHandler;
import io.airlift.mcp.client.McpInputRequestsHandler.ProcessorResult;
import io.airlift.mcp.client.McpInputRequestsHandler.ResultProcessor;
import io.airlift.mcp.client.McpInputRequestsHandler.TaskResultProcessor;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.client.McpConnectionSetting.LEGACY_ELICITATION_HANDLER;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

public class InternalResultProcessor
        implements TaskResultProcessor, ResultProcessor
{
    private final McpInputRequestsHandler handler;
    private final Optional<Map<String, Object>> meta;

    public InternalResultProcessor(McpInputRequestsHandler handler, Optional<Map<String, Object>> meta)
    {
        this.handler = requireNonNull(handler, "handler is null");
        this.meta = requireNonNull(meta, "meta is null").map(ImmutableMap::copyOf);
    }

    @Override
    public <T extends InputResponses<T>, R extends InputRequests<R>, U> ProcessorResult<U> process(McpConnection connection, T initialRequest, BiFunction<McpConnection, T, R> resultSupplier, Function<R, Optional<U>> mapper)
    {
        connection = connection.withSetting(LEGACY_ELICITATION_HANDLER, handler.asLegacyElicitationHandler());

        int roundsRemaining = connection.setting(MAX_INPUT_REQUEST_ROUNDS);
        T currentRequest = initialRequest;
        while (true) {
            R result = resultSupplier.apply(connection, currentRequest);
            Map<String, InputRequest> inputRequests = result.inputRequests().orElseGet(ImmutableMap::of);
            if (inputRequests.isEmpty()) {
                Optional<U> applied = mapper.apply(result);
                return () -> applied;
            }

            if (roundsRemaining-- <= 0) {
                throw exception(INVALID_REQUEST, "Too many input request rounds").asClientException();
            }

            Map<String, Object> inputResponses = handler.handleInputRequests(inputRequests);
            currentRequest = currentRequest.withInputResponses(result.requestState(), inputResponses);
        }
    }

    @Override
    public <U> ProcessorResult<U> process(McpTasksConnection connection, ToolResult toolResult, Function<CallToolResult, Optional<U>> mapper)
            throws InterruptedException
    {
        int roundsRemaining = connection.setting(MAX_INPUT_REQUEST_ROUNDS);
        ProcessorResult<U> result = null;
        do {
            toolResult = switch (toolResult) {
                case CallToolResult callToolResult -> {
                    Optional<U> applied = mapper.apply(callToolResult);
                    result = () -> applied;
                    yield toolResult;
                }

                case Task task -> {
                    if ((task.status() == TaskStatus.INPUT_REQUIRED) && (roundsRemaining-- <= 0)) {
                        throw exception(INVALID_REQUEST, "Too many input request rounds").asClientException();
                    }
                    yield processTask(connection, task);
                }
            };
        }
        while (result == null);

        return result;
    }

    private ToolResult processTask(McpTasksConnection connection, Task task)
            throws InterruptedException
    {
        return switch (task.status()) {
            case WORKING -> {
                connection.sleepTask(task);
                yield connection.getTask(new GetTaskRequest(task.taskId(), meta));
            }

            case FAILED, CANCELLED -> {
                JsonRpcErrorDetail errorDetail = task.error().orElseThrow(() -> exception(INVALID_PARAMS, "Error details missing"));
                throw exception(errorDetail.code(), errorDetail.message(), errorDetail.data());
            }

            case COMPLETED -> task.result().orElseThrow(() -> exception(INVALID_PARAMS, "Task results missing"));

            case INPUT_REQUIRED -> {
                Map<String, InputRequest> inputRequests = task.inputRequests().orElseGet(ImmutableMap::of);
                connection.updateTask(new UpdateTaskRequest(task.taskId(), handler.handleInputRequests(inputRequests), meta));

                connection.sleepTask(task);
                yield connection.getTask(new GetTaskRequest(task.taskId(), meta));
            }
        };
    }
}

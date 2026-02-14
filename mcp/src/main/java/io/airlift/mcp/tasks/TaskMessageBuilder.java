package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Meta;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.airlift.mcp.model.Constants.META_RELATED_TASK;
import static java.util.Objects.requireNonNull;

public class TaskMessageBuilder
{
    private final String taskId;
    private final Object requestId;

    private TaskMessageBuilder(String taskId, Object requestId)
    {
        this.taskId = requireNonNull(taskId, "taskId is null");
        this.requestId = requireNonNull(requestId, "requestId is null");
    }

    public static TaskMessageBuilder builder(TaskFacade taskFacade)
    {
        return new TaskMessageBuilder(taskFacade.taskId(), taskFacade.requestId());
    }

    public static TaskMessageBuilder builder(String taskId, Object requestId)
    {
        return new TaskMessageBuilder(taskId, requestId);
    }

    public static TaskMessageBuilder builder(String taskId)
    {
        return new TaskMessageBuilder(taskId, UUID.randomUUID().toString());
    }

    public Object requestId()
    {
        return requestId;
    }

    public JsonRpcResponse<?> buildError(JsonRpcErrorDetail errorDetail)
    {
        return new JsonRpcResponse<>(requestId, Optional.of(errorDetail), Optional.empty());
    }

    public <R> JsonRpcResponse<R> buildResponse(R response, Class<R> type)
    {
        return new JsonRpcResponse<>(requestId, Optional.empty(), Optional.of(applyMeta(response, type)));
    }

    public <T> JsonRpcRequest<T> buildRequest(String method, T params, Class<T> type)
    {
        requireNonNull(params, "params is null");

        return JsonRpcRequest.buildRequest(requestId, method, applyMeta(params, type));
    }

    private <T> T applyMeta(T value, Class<T> type)
    {
        if (value instanceof Meta meta) {
            Map<String, Object> current = meta.meta().orElseGet(ImmutableMap::of);
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            builder.putAll(current);
            builder.put(META_RELATED_TASK, taskId);
            value = type.cast(meta.withMeta(builder.buildKeepingLast()));
        }

        return value;
    }
}

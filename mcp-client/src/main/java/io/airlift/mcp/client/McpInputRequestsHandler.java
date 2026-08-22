package io.airlift.mcp.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.client.internal.InternalResultProcessor;
import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.ToolResult;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;

public interface McpInputRequestsHandler
{
    Map<String, Object> handleInputRequests(Map<String, InputRequest> inputRequests);

    default LegacyElicitationHandler asLegacyElicitationHandler()
    {
        return elicitRequest -> {
            Map<String, Object> responses = handleInputRequests(ImmutableMap.of("request", new InputRequest(METHOD_ELICITATION_CREATE, elicitRequest)));
            return (ElicitResult) responses.get("request");
        };
    }

    interface ProcessorResult<U>
    {
        Optional<U> optional();

        default U required()
        {
            return optional().orElseThrow(() -> exception(INVALID_PARAMS, "Expected result"));
        }
    }

    interface ResultProcessor
    {
        <T extends InputResponses<T>, R extends InputRequests<R>, U> ProcessorResult<U> process(McpConnection connection, T initialRequest, BiFunction<McpConnection, T, R> resultSupplier, Function<R, Optional<U>> mapper);
    }

    interface TaskResultProcessor
    {
        <U> ProcessorResult<U> process(McpTasksConnection connection, ToolResult toolResult, Function<CallToolResult, Optional<U>> mapper)
                throws InterruptedException;
    }

    default ResultProcessor asResultProcessor()
    {
        return new InternalResultProcessor(this, Optional.empty());
    }

    default TaskResultProcessor asTaskResultProcessor()
    {
        return new InternalResultProcessor(this, Optional.empty());
    }

    default TaskResultProcessor asTaskResultProcessor(Optional<Map<String, Object>> meta)
    {
        return new InternalResultProcessor(this, meta);
    }
}

package io.airlift.mcp.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.McpJacksonSubTypes.buildJacksonSubType;

public final class McpMapper
{
    // backdoor in case the JSON mapper needs to be replaced at runtime
    @Inject
    private static volatile JsonMapper jsonMapper;

    static {
        jsonMapper = new JsonMapperProvider()
                .withJacksonSubTypes(ImmutableSet.of(buildJacksonSubType()))
                .get();
    }

    private McpMapper() {}

    public static JsonMapper jsonMapper()
    {
        return jsonMapper;
    }

    public static LoggingMessageNotification requireLoggingMessageNotification(Optional<Object> params)
    {
        Object value = params.orElseThrow();    // TODO
        return jsonMapper.convertValue(value, LoggingMessageNotification.class);
    }

    public static ProgressNotification requireProgressNotification(Optional<Object> params)
    {
        Object value = params.orElseThrow();    // TODO
        return jsonMapper.convertValue(value, ProgressNotification.class);
    }

    public static CallToolResult requireCallToolResult(ToolResult toolResult)
    {
        if (toolResult instanceof CallToolResult callToolResult) {
            return callToolResult;
        }
        throw exception(INVALID_PARAMS, "Expected CallToolResult, got " + toolResult).asClientException();
    }

    public static <T> T requireStructuredContent(CallToolResult callToolResult, Class<T> resultType)
    {
        return optionalStructuredContent(callToolResult, resultType)
                .orElseThrow(() -> exception(INVALID_PARAMS, "Expected structured content"));
    }

    public static <T> Optional<T> optionalStructuredContent(CallToolResult callToolResult, Class<T> resultType)
    {
        return callToolResult.structuredContent()
                .map(StructuredContent::value)
                .map(value -> jsonMapper.convertValue(value, resultType));
    }

    public static Map<String, InputRequest> requireInputRequests(ToolResult toolResult)
    {
        if (toolResult instanceof Task task) {
            if (task.inputRequests().isPresent()) {
                return task.inputRequests().orElseThrow();
            }
        }
        throw exception(INVALID_PARAMS, "Expected InputRequests, got " + toolResult).asClientException();
    }

    public static String requireContentString(CallToolResult callToolResult)
    {
        return optionalContentString(callToolResult)
                .orElseThrow(() -> exception(INVALID_PARAMS, "Result has no content").asClientException());
    }

    public static Optional<String> optionalContentString(CallToolResult callToolResult)
    {
        return callToolResult.content()
                .map(content -> {
                    if (content.isEmpty()) {
                        throw exception(INVALID_PARAMS, "Content is empty").asClientException();
                    }
                    return content.getFirst();
                })
                .map(firstContent -> {
                    if (firstContent instanceof TextContent(var text, _)) {
                        return text;
                    }
                    throw exception(INVALID_PARAMS, "Expected TextContent, got " + firstContent.getClass().getName()).asClientException();
                });
    }
}

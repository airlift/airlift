package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.model.Content.TextContent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNullElse;

public record CallToolResult(
        Optional<List<Content>> content,
        Optional<StructuredContent<?>> structuredContent,
        Optional<Boolean> isError,
        Optional<ResultType> resultType,
        Optional<String> requestState,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<Map<String, Object>> meta)
        implements InputRequests,
                   Meta,
                   Result,
                   TaskHandlerResult
{
    public static InputRequests.Builder<CallToolResult> inputRequestsBuilder()
    {
        return new Builder<>()
        {
            @Override
            public CallToolResult build()
            {
                return new CallToolResult(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(INPUT_REQUIRED),
                        requestState,
                        Optional.of(inputRequests.buildOrThrow()),
                        Optional.empty());
            }
        };
    }

    public CallToolResult
    {
        content = requireNonNullElse(content, Optional.empty());
        structuredContent = requireNonNullElse(structuredContent, Optional.empty());
        isError = requireNonNullElse(isError, Optional.empty());
        resultType = requireNonNullElse(resultType, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public CallToolResult(
            List<Content> content,
            Optional<StructuredContent<?>> structuredContent,
            boolean isError,
            Optional<Map<String, Object>> meta)
    {
        this(Optional.of(content), structuredContent, Optional.of(isError), Optional.empty(), Optional.empty(), Optional.empty(), meta);
    }

    public CallToolResult(List<Content> content, Optional<StructuredContent<?>> structuredContent, boolean isError)
    {
        this(content, structuredContent, isError, Optional.empty());
    }

    public CallToolResult(Content content)
    {
        this(ImmutableList.of(content), Optional.empty(), false, Optional.empty());
    }

    public CallToolResult(List<Content> content)
    {
        this(content, Optional.empty(), false, Optional.empty());
    }

    public static CallToolResult errorResult(String errorMessage)
    {
        return new CallToolResult(ImmutableList.of(new TextContent(errorMessage)), Optional.empty(), true, Optional.empty());
    }

    @Override
    public CallToolResult withInputRequests(Optional<ResultType> resultType, Optional<String> requestState, Optional<Map<String, InputRequest>> inputRequests)
    {
        return new CallToolResult(content, structuredContent, isError, resultType, requestState, inputRequests, meta);
    }

    @Override
    public CallToolResult withMeta(Map<String, Object> meta)
    {
        return new CallToolResult(content, structuredContent, isError, resultType, requestState, inputRequests, Optional.of(meta));
    }

    public static CallToolResult forError(McpClientException mcpClientException)
    {
        return new CallToolResult(ImmutableList.of(new Content.TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true, Optional.empty());
    }

    public CallToolResult withResultType(ResultType resultType)
    {
        return new CallToolResult(content, structuredContent, isError, Optional.of(resultType), requestState, inputRequests, meta);
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptResult(
        Optional<String> description,
        Optional<List<PromptMessage>> messages,
        Optional<ResultType> resultType,
        Optional<String> requestState,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<Map<String, Object>> meta)
        implements InputRequests, Meta
{
    public static InputRequests.Builder<GetPromptResult> inputRequestsBuilder()
    {
        return new Builder<>()
        {
            @Override
            public GetPromptResult build()
            {
                return new GetPromptResult(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(INPUT_REQUIRED),
                        requestState,
                        Optional.of(inputRequests.buildOrThrow()),
                        Optional.empty());
            }
        };
    }

    public record PromptMessage(Role role, Content content)
    {
        public PromptMessage
        {
            requireNonNull(role, "role is null");
            requireNonNull(content, "content is null");
        }
    }

    public GetPromptResult
    {
        description = requireNonNullElse(description, Optional.empty());
        messages = requireNonNullElse(messages, Optional.empty());
        resultType = requireNonNullElse(resultType, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public GetPromptResult(String result)
    {
        this(Optional.empty(), ImmutableList.of(new PromptMessage(Role.USER, new Content.TextContent(result))));
    }

    public GetPromptResult(Optional<String> description, List<PromptMessage> messages)
    {
        this(description, Optional.of(messages), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public GetPromptResult withInputRequests(Optional<ResultType> resultType, Optional<String> requestState, Optional<Map<String, InputRequest>> inputRequests)
    {
        return new GetPromptResult(description, messages, resultType, requestState, inputRequests, meta);
    }

    @Override
    public GetPromptResult withMeta(Map<String, Object> meta)
    {
        return new GetPromptResult(description, messages, resultType, requestState, inputRequests, Optional.of(meta));
    }
}

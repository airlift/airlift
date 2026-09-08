package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
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
        implements InputRequests<GetPromptResult>,
                   Meta<GetPromptResult>,
                   Result
{
    private static final Factory<GetPromptResult> FACTORY = (requestState, inputRequests) -> new GetPromptResult(
            Optional.empty(),
            Optional.empty(),
            Optional.of(INPUT_REQUIRED),
            requestState,
            Optional.of(inputRequests),
            Optional.empty());

    public static Builder<GetPromptResult> inputRequestsBuilder()
    {
        return InputRequests.builder(FACTORY);
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
        messages = requireNonNullElse(messages, Optional.<List<PromptMessage>>empty()).map(ImmutableList::copyOf);
        resultType = requireNonNullElse(resultType, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.<Map<String, InputRequest>>empty()).map(ImmutableMap::copyOf);
        meta = requireNonNullElse(meta, Optional.empty());
        meta = normalize(meta);
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

    public GetPromptResult withResultType(ResultType resultType)
    {
        return new GetPromptResult(description, messages, Optional.of(resultType), requestState, inputRequests, meta);
    }
}

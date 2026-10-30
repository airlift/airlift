package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptResult(
        Optional<String> description,
        Optional<List<PromptMessage>> messages,
        Optional<String> requestState,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<Map<String, Object>> meta)
        implements InputRequests, Meta<GetPromptResult>
{
    private static final Factory<GetPromptResult> FACTORY = (requestState, inputRequests) -> new GetPromptResult(
            Optional.empty(),
            Optional.empty(),
            requestState,
            Optional.of(inputRequests),
            Optional.empty());

    public static InputRequests.Builder<GetPromptResult> inputRequestsBuilder()
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
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.<Map<String, InputRequest>>empty()).map(ImmutableMap::copyOf);
        meta = normalize(meta);
    }

    public GetPromptResult(String result)
    {
        this(Optional.empty(), ImmutableList.of(new PromptMessage(Role.USER, new Content.TextContent(result))));
    }

    public GetPromptResult(Optional<String> description, List<PromptMessage> messages)
    {
        this(description, Optional.of(messages), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public GetPromptResult withMeta(Map<String, Object> meta)
    {
        return new GetPromptResult(description, messages, requestState, inputRequests, Optional.of(meta));
    }
}

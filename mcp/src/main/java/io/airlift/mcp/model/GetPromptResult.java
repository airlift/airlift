package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetPromptResult(Optional<String> description, List<PromptMessage> messages, Optional<Map<String, Object>> meta)
        implements GetPromptResponse
{
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
        messages = ImmutableList.copyOf(messages);
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public GetPromptResult(Optional<String> description, List<PromptMessage> messages)
    {
        this(description, messages, Optional.empty());
    }

    @Override
    public Optional<ResultType> resultType()
    {
        return Optional.of(COMPLETE);
    }

    @Override
    public GetPromptResult withMeta(Map<String, Object> meta)
    {
        return new GetPromptResult(description, messages, Optional.of(meta));
    }
}

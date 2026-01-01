package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record GetPromptResult(Optional<String> description, List<PromptMessage> messages)
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
        description = firstNonNull(description, Optional.empty());
        messages = ImmutableList.copyOf(messages);
    }
}

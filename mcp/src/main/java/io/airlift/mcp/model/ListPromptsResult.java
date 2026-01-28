package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListPromptsResult
    {
        prompts = ImmutableList.copyOf(prompts);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
    }

    public ListPromptsResult(List<Prompt> prompts)
    {
        this(prompts, Optional.empty());
    }
}

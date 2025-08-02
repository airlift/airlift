package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor)
        implements Paginated
{
    public ListPromptsResult
    {
        prompts = ImmutableList.copyOf(prompts);
        requireNonNull(nextCursor, "nextCursor is null");
    }
}

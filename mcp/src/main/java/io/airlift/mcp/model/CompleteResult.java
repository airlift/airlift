package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public record CompleteResult(CompleteCompletion completion)
{
    public CompleteResult
    {
        requireNonNull(completion, "completion is null");
    }

    public record CompleteCompletion(List<String> values, OptionalInt total, Optional<Boolean> hasMore)
    {
        public CompleteCompletion
        {
            values = ImmutableList.copyOf(values);
            requireNonNull(total, "total is null");
            requireNonNull(hasMore, "hasMore is null");
        }
    }
}

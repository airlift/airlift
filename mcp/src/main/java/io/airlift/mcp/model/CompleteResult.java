package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CompleteResult(CompleteCompletion completion)
{
    // see: https://modelcontextprotocol.io/specification/2025-03-26/server/utilities/completion#completeresult
    public static final int MAX_COMPLETIONS = 100;

    public CompleteResult
    {
        requireNonNull(completion, "completion is null");
    }

    public record CompleteCompletion(List<String> values, OptionalInt total, OptionalBoolean hasMore)
    {
        public CompleteCompletion
        {
            values = ImmutableList.copyOf(values);
            total = requireNonNullElse(total, OptionalInt.empty());
            hasMore = requireNonNullElse(hasMore, OptionalBoolean.UNDEFINED);

            checkArgument(values.size() <= MAX_COMPLETIONS, "values exceeds max completions");
        }
    }
}

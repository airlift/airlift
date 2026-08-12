package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CompleteResult(CompleteCompletion completion, Optional<Map<String, Object>> meta)
        implements Meta<CompleteResult>
{
    // see: https://modelcontextprotocol.io/specification/2025-03-26/server/utilities/completion#completeresult
    public static final int MAX_COMPLETIONS = 100;

    public CompleteResult
    {
        requireNonNull(completion, "completion is null");
        meta = normalize(meta);
    }

    public CompleteResult(CompleteCompletion completion)
    {
        this(completion, Optional.empty());
    }

    public static CompleteResult empty()
    {
        return new CompleteResult(new CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED), Optional.empty());
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

    @Override
    public CompleteResult withMeta(Map<String, Object> meta)
    {
        return new CompleteResult(completion, Optional.of(meta));
    }
}

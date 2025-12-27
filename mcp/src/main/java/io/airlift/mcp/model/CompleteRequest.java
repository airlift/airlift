package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CompleteRequest(CompleteReference ref, CompleteArgument argument, Optional<CompleteContext> context, Optional<Map<String, Object>> meta)
        implements Meta
{
    public CompleteRequest
    {
        requireNonNull(ref, "ref is null");
        requireNonNull(argument, "argument is null");
        requireNonNull(context, "context is null");
        meta = firstNonNull(meta, Optional.empty());
    }

    public CompleteRequest(CompleteReference ref, CompleteArgument argument, Optional<CompleteContext> context)
    {
        this(ref, argument, context, Optional.empty());
    }

    public record CompleteArgument(String name, String value)
    {
        public CompleteArgument
        {
            requireNonNull(name, "name is null");
            requireNonNull(value, "value is null");
        }
    }

    public record CompleteContext(Map<String, String> arguments)
    {
        public CompleteContext
        {
            arguments = ImmutableMap.copyOf(arguments);
        }
    }
}

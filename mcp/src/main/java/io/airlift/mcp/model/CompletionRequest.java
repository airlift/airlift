package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CompletionRequest(CompletionReference ref, Argument argument, Optional<Context> context, Optional<Map<String, Object>> meta)
        implements Meta
{
    public record Argument(String name, String value)
    {
        public Argument
        {
            requireNonNull(name, "value is null");
            requireNonNull(value, "value is null");
        }
    }

    public record Context(Map<String, Object> arguments)
    {
        public Context
        {
            arguments = ImmutableMap.copyOf(arguments);
        }
    }

    public CompletionRequest
    {
        requireNonNull(ref, "ref is null");
        requireNonNull(argument, "argument is null");
        requireNonNull(context, "context is null");
    }

    public CompletionRequest(CompletionReference ref, Argument argument, Optional<Context> context)
    {
        this(ref, argument, context, Optional.empty());
    }
}

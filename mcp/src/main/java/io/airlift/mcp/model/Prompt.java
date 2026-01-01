package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments)
{
    public record Argument(String name, Optional<String> description, boolean required)
    {
        public Argument
        {
            requireNonNull(name, "name is null");
            description = firstNonNull(description, Optional.empty());
        }
    }

    public Prompt
    {
        requireNonNull(name, "name is null");
        description = firstNonNull(description, Optional.empty());
        role = firstNonNull(role, Optional.empty());
        arguments = ImmutableList.copyOf(arguments);
    }
}

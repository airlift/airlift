package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments, Optional<List<Icon>> icons)
{
    public Prompt
    {
        requireNonNull(name, "name is null");
        description = requireNonNullElse(description, Optional.empty());
        role = requireNonNullElse(role, Optional.empty());
        arguments = ImmutableList.copyOf(arguments);
        icons = requireNonNullElse(icons, Optional.empty());
    }

    public Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments)
    {
        this(name, description, role, arguments, Optional.empty());
    }

    public Prompt withIcons(Optional<List<Icon>> icons)
    {
        return new Prompt(name, description, role, arguments, icons);
    }

    public Prompt withoutIcons()
    {
        return new Prompt(name, description, role, arguments, Optional.empty());
    }

    public record Argument(String name, Optional<String> description, boolean required)
    {
        public Argument
        {
            requireNonNull(name, "name is null");
            description = requireNonNullElse(description, Optional.empty());
        }
    }
}

package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments, Optional<List<Icon>> icons, Optional<Map<String, Object>> meta)
        implements Meta
{
    public Prompt
    {
        requireNonNull(name, "name is null");
        description = requireNonNullElse(description, Optional.empty());
        role = requireNonNullElse(role, Optional.empty());
        arguments = ImmutableList.copyOf(arguments);
        icons = requireNonNullElse(icons, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments)
    {
        this(name, description, role, arguments, Optional.empty(), Optional.empty());
    }

    @Override
    public Prompt withMeta(Map<String, Object> meta)
    {
        return new Prompt(name, description, role, arguments, icons, Optional.of(meta));
    }

    public Prompt withIcons(Optional<List<Icon>> icons)
    {
        return new Prompt(name, description, role, arguments, icons, meta);
    }

    public Prompt withoutIcons()
    {
        return new Prompt(name, description, role, arguments, Optional.empty(), meta);
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

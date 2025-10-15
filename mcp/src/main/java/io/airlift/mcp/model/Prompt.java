package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

public record Prompt(String name, Optional<String> description, Optional<Role> role, List<Argument> arguments) {
    public record Argument(String name, Optional<String> description, boolean required) {
        public Argument {
            requireNonNull(name, "name is null");
            requireNonNull(description, "description is null");
        }
    }

    public Prompt {
        requireNonNull(name, "name is null");
        requireNonNull(description, "description is null");
        requireNonNull(role, "role is null");
        arguments = ImmutableList.copyOf(arguments);
    }
}

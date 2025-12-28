package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

public record Tool(
        String name,
        Optional<String> description,
        Optional<String> title,
        ObjectNode inputSchema,
        Optional<ObjectNode> outputSchema,
        ToolAnnotations annotations,
        Optional<List<Icon>> icons,
        Optional<ToolExecution> execution)
{
    private static final Set<Character> ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.".chars().mapToObj(i -> (char) i).collect(toImmutableSet());

    public Tool
    {
        requireNonNull(name, "name is null");
        description = firstNonNull(description, Optional.empty());
        title = firstNonNull(title, Optional.empty());
        requireNonNull(inputSchema, "inputSchema is null");
        outputSchema = firstNonNull(outputSchema, Optional.empty());
        requireNonNull(annotations, "annotations is null");
        icons = firstNonNull(icons, Optional.empty());
        execution = firstNonNull(execution, Optional.empty());

        validateName(name);
    }

    public Tool(String name, Optional<String> description, Optional<String> title, ObjectNode inputSchema, Optional<ObjectNode> outputSchema, ToolAnnotations annotations)
    {
        this(name, description, title, inputSchema, outputSchema, annotations, Optional.empty(), Optional.empty());
    }

    public static void validateName(String name)
    {
        // see: https://modelcontextprotocol.io/specification/2025-11-25/server/tools#tool-names

        requireNonNull(name, "name is null");
        checkState(!name.isEmpty() && name.length() <= 128, "Tool name must be between 1 and 128 characters");
        checkState(name.chars().allMatch(c -> ALLOWED_CHARACTERS.contains((char) c)), "Tool name must be between 1 and 128 characters");
    }

    public Tool withIcons(Optional<List<Icon>> icons)
    {
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, icons, execution);
    }

    public Tool withoutIcons()
    {
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, Optional.empty(), execution);
    }

    public Tool withoutExecution()
    {
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, icons, Optional.empty());
    }

    public record ToolAnnotations(
            Optional<String> title,
            Optional<Boolean> readOnlyHint,
            Optional<Boolean> destructiveHint,
            Optional<Boolean> idempotentHint,
            Optional<Boolean> openWorldHint)
    {
        public ToolAnnotations
        {
            title = firstNonNull(title, Optional.empty());
            readOnlyHint = firstNonNull(readOnlyHint, Optional.empty());
            destructiveHint = firstNonNull(destructiveHint, Optional.empty());
            idempotentHint = firstNonNull(idempotentHint, Optional.empty());
            openWorldHint = firstNonNull(openWorldHint, Optional.empty());
        }
    }
}

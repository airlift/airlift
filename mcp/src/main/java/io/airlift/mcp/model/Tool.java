package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Tool(
        String name,
        Optional<String> description,
        Optional<String> title,
        ObjectNode inputSchema,
        Optional<ObjectNode> outputSchema,
        ToolAnnotations annotations,
        Optional<List<Icon>> icons,
        Optional<Map<String, Object>> meta)
        implements Meta
{
    private static final Set<Character> ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.".chars().mapToObj(i -> (char) i).collect(toImmutableSet());

    public Tool
    {
        requireNonNull(name, "name is null");
        description = requireNonNullElse(description, Optional.empty());
        title = requireNonNullElse(title, Optional.empty());
        requireNonNull(inputSchema, "inputSchema is null");
        outputSchema = requireNonNullElse(outputSchema, Optional.empty());
        requireNonNull(annotations, "annotations is null");
        icons = requireNonNullElse(icons, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());

        validateName(name);
    }

    public Tool(String name, Optional<String> description, Optional<String> title, ObjectNode inputSchema, Optional<ObjectNode> outputSchema, ToolAnnotations annotations, Optional<List<Icon>> icons)
    {
        this(name, description, title, inputSchema, outputSchema, annotations, icons, Optional.empty());
    }

    public Tool(String name, Optional<String> description, Optional<String> title, ObjectNode inputSchema, Optional<ObjectNode> outputSchema, ToolAnnotations annotations)
    {
        this(name, description, title, inputSchema, outputSchema, annotations, Optional.empty(), Optional.empty());
    }

    @Override
    public Tool withMeta(Map<String, Object> meta)
    {
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, icons, Optional.of(meta));
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
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, icons, meta);
    }

    public Tool withoutIcons()
    {
        return new Tool(name, description, title, inputSchema, outputSchema, annotations, Optional.empty(), meta);
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
            title = requireNonNullElse(title, Optional.empty());
            readOnlyHint = requireNonNullElse(readOnlyHint, Optional.empty());
            destructiveHint = requireNonNullElse(destructiveHint, Optional.empty());
            idempotentHint = requireNonNullElse(idempotentHint, Optional.empty());
            openWorldHint = requireNonNullElse(openWorldHint, Optional.empty());
        }
    }
}

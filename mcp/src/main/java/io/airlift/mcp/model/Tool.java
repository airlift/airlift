package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Tool(String name, Optional<String> description, ObjectNode inputSchema, Optional<ObjectNode> outputSchema, ToolAnnotations annotations)
{
    public record ToolAnnotations(
            Optional<String> title,
            Optional<Boolean> readOnlyHint,
            Optional<Boolean> destructiveHint,
            Optional<Boolean> idempotentHint,
            Optional<Boolean> openWorldHint,
            Optional<Boolean> returnDirect)
    {
        public ToolAnnotations
        {
            requireNonNull(title, "title is null");
            requireNonNull(readOnlyHint, "readOnlyHint is null");
            requireNonNull(destructiveHint, "destructiveHint is null");
            requireNonNull(idempotentHint, "idempotentHint is null");
            requireNonNull(openWorldHint, "openWorldHint is null");
            requireNonNull(returnDirect, "returnDirect is null");
        }
    }

    public Tool
    {
        requireNonNull(name, "name is null");
        requireNonNull(description, "description is null");
        requireNonNull(inputSchema, "inputSchema is null");
        requireNonNull(outputSchema, "outputSchema is null");
        requireNonNull(annotations, "annotations is null");
    }
}

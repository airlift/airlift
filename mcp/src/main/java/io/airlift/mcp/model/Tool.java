package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
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
            title = firstNonNull(title, Optional.empty());
            readOnlyHint = firstNonNull(readOnlyHint, Optional.empty());
            destructiveHint = firstNonNull(destructiveHint, Optional.empty());
            idempotentHint = firstNonNull(idempotentHint, Optional.empty());
            openWorldHint = firstNonNull(openWorldHint, Optional.empty());
            returnDirect = firstNonNull(returnDirect, Optional.empty());
        }
    }

    public Tool
    {
        requireNonNull(name, "name is null");
        description = firstNonNull(description, Optional.empty());
        outputSchema = firstNonNull(outputSchema, Optional.empty());
        requireNonNull(annotations, "annotations is null");
    }
}

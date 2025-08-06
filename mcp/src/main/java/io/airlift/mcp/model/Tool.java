package io.airlift.mcp.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.mcp.model.OptionalBoolean.UNDEFINED;
import static java.util.Objects.requireNonNull;

public record Tool(String name, Optional<String> description, ObjectNode inputSchema, Optional<ObjectNode> outputSchema, ToolAnnotations annotations)
{
    public record ToolAnnotations(
            Optional<String> title,
            OptionalBoolean readOnlyHint,
            OptionalBoolean destructiveHint,
            OptionalBoolean idempotentHint,
            OptionalBoolean openWorldHint,
            OptionalBoolean returnDirect)
    {
        public ToolAnnotations
        {
            requireNonNull(title, "title is null");
            readOnlyHint = firstNonNull(readOnlyHint, UNDEFINED);
            destructiveHint = firstNonNull(destructiveHint, UNDEFINED);
            idempotentHint = firstNonNull(idempotentHint, UNDEFINED);
            openWorldHint = firstNonNull(openWorldHint, UNDEFINED);
            returnDirect = firstNonNull(returnDirect, UNDEFINED);
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

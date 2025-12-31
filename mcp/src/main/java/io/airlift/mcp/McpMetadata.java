package io.airlift.mcp;

import io.airlift.mcp.model.Implementation;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record McpMetadata(String uriPath, Implementation implementation, Optional<String> instructions)
{
    public static final McpMetadata DEFAULT = new McpMetadata("/mcp");

    public McpMetadata
    {
        requireNonNull(uriPath, "uriPath is null");
        requireNonNull(implementation, "implementation is null");
        requireNonNull(instructions, "instructions is null");
    }

    public McpMetadata(String uriPath)
    {
        this(uriPath, new Implementation("mcp", "1.0.0"), Optional.empty());
    }

    public McpMetadata withImplementation(Implementation implementation)
    {
        return new McpMetadata(uriPath, implementation, instructions);
    }

    public McpMetadata withInstructions(String instructions)
    {
        return new McpMetadata(uriPath, implementation, Optional.ofNullable(instructions));
    }
}

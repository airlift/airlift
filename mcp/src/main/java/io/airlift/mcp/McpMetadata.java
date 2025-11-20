package io.airlift.mcp;

import io.airlift.mcp.model.Implementation;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record McpMetadata(String uriPath, Implementation implementation, Optional<String> instructions, boolean tools, boolean prompts, boolean resources, boolean completions)
{
    public static final String CONTEXT_REQUEST_KEY = McpMetadata.class.getName() + ".request";

    public McpMetadata
    {
        requireNonNull(uriPath, "uriPath is null");
        requireNonNull(implementation, "implementation is null");
        requireNonNull(instructions, "instructions is null");
    }

    public McpMetadata(String uriPath)
    {
        this(uriPath, new Implementation("mcp", "1.0.0"), Optional.empty(), false, false, false, false);
    }

    public McpMetadata withImplementation(Implementation implementation)
    {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources, completions);
    }

    public McpMetadata withInstructions(String instructions)
    {
        return new McpMetadata(uriPath, implementation, Optional.ofNullable(instructions), tools, prompts, resources, completions);
    }

    public McpMetadata withTools(boolean tools)
    {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources, completions);
    }

    public McpMetadata withPrompts(boolean prompts)
    {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources, completions);
    }

    public McpMetadata withResources(boolean resources)
    {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources, completions);
    }

    public McpMetadata withCompletions(boolean completions)
    {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources, completions);
    }
}

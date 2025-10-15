package io.airlift.mcp;

import static java.util.Objects.requireNonNull;

import io.airlift.mcp.model.Implementation;
import java.util.Optional;

public record McpMetadata(
        String uriPath,
        Implementation implementation,
        Optional<String> instructions,
        boolean tools,
        boolean prompts,
        boolean resources) {
    public static final String CONTEXT_REQUEST_KEY = McpMetadata.class.getName();

    public McpMetadata {
        requireNonNull(uriPath, "uriPath is null");
        requireNonNull(implementation, "implementation is null");
        requireNonNull(instructions, "instructions is null");
    }

    public McpMetadata(String uriPath) {
        this(uriPath, new Implementation("mcp", "1.0.0"), Optional.empty(), false, false, false);
    }

    public McpMetadata withImplementation(Implementation implementation) {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources);
    }

    public McpMetadata withInstructions(String instructions) {
        return new McpMetadata(uriPath, implementation, Optional.ofNullable(instructions), tools, prompts, resources);
    }

    public McpMetadata withTools(boolean tools) {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources);
    }

    public McpMetadata withPrompts(boolean prompts) {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources);
    }

    public McpMetadata withResources(boolean resources) {
        return new McpMetadata(uriPath, implementation, instructions, tools, prompts, resources);
    }
}

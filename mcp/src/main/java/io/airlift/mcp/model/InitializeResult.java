package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        Implementation serverInfo,
        Optional<String> instructions)
{
    public record ServerCapabilities(Optional<CompletionCapabilities> completions, Optional<LoggingCapabilities> logging, Optional<ListChanged> prompts, Optional<SubscribeListChanged> resources, Optional<ListChanged> tools, Optional<Map<String, Object>> experimental)
            implements Experimental
    {
        public ServerCapabilities
        {
            completions = requireNonNullElse(completions, Optional.empty());
            logging = requireNonNullElse(logging, Optional.empty());
            prompts = requireNonNullElse(prompts, Optional.empty());
            resources = requireNonNullElse(resources, Optional.empty());
            tools = requireNonNullElse(tools, Optional.empty());
            experimental = requireNonNullElse(experimental, Optional.empty());
        }

        public ServerCapabilities()
        {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CompletionCapabilities() {}

    public record LoggingCapabilities() {}

    public InitializeResult
    {
        requireNonNull(protocolVersion, "protocolVersion is null");
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(serverInfo, "serverInfo is null");
        instructions = requireNonNullElse(instructions, Optional.empty());
    }
}

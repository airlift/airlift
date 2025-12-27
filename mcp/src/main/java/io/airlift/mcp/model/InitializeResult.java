package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        Implementation serverInfo,
        Optional<String> instructions)
{
    public record ServerCapabilities(Optional<CompletionCapabilities> completions, Optional<LoggingCapabilities> logging, Optional<ListChanged> prompts, Optional<SubscribeListChanged> resources, Optional<ListChanged> tools)
    {
        public ServerCapabilities
        {
            requireNonNull(completions, "completions is null");
            requireNonNull(logging, "logging is null");
            requireNonNull(prompts, "prompts is null");
            requireNonNull(resources, "resources is null");
            requireNonNull(tools, "tools is null");
        }

        public ServerCapabilities()
        {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CompletionCapabilities() {}

    public record LoggingCapabilities() {}

    public InitializeResult
    {
        requireNonNull(protocolVersion, "protocolVersion is null");
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(serverInfo, "serverInfo is null");
        requireNonNull(instructions, "instructions is null");
    }
}

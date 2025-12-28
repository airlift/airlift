package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

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
            completions = firstNonNull(completions, Optional.empty());
            logging = firstNonNull(logging, Optional.empty());
            prompts = firstNonNull(prompts, Optional.empty());
            resources = firstNonNull(resources, Optional.empty());
            tools = firstNonNull(tools, Optional.empty());
            experimental = firstNonNull(experimental, Optional.empty());
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
        instructions = firstNonNull(instructions, Optional.empty());
    }
}

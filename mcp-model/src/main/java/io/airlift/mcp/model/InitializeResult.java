package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        Implementation serverInfo,
        Optional<String> instructions)
{
    public record ServerCapabilities(Optional<CompletionCapabilities> completions, Optional<LoggingCapabilities> logging, Optional<ListChanged> prompts, Optional<SubscribeListChanged> resources, Optional<ListChanged> tools, Optional<Map<String, Object>> extensions, Optional<Map<String, Object>> experimental)
            implements Experimental
    {
        public ServerCapabilities
        {
            completions = requireNonNullElse(completions, Optional.empty());
            logging = requireNonNullElse(logging, Optional.empty());
            prompts = requireNonNullElse(prompts, Optional.empty());
            resources = requireNonNullElse(resources, Optional.empty());
            tools = requireNonNullElse(tools, Optional.empty());
            extensions = requireNonNullElse(extensions, Optional.empty());
            experimental = requireNonNullElse(experimental, Optional.empty());
        }

        public ServerCapabilities()
        {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CompletionCapabilities() {}

    public record LoggingCapabilities() {}

    public InitializeResult
    {
        protocolVersion = requireNonNullElse(protocolVersion, "");
        capabilities = requireNonNullElse(capabilities, new ServerCapabilities());
        serverInfo = requireNonNullElse(serverInfo, new Implementation("", ""));
        instructions = requireNonNullElse(instructions, Optional.empty());
    }
}

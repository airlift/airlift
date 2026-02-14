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
    public InitializeResult
    {
        requireNonNull(protocolVersion, "protocolVersion is null");
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(serverInfo, "serverInfo is null");
        instructions = requireNonNullElse(instructions, Optional.empty());
    }

    public record ServerCapabilities(
            Optional<CompletionCapabilities> completions,
            Optional<LoggingCapabilities> logging,
            Optional<ListChanged> prompts,
            Optional<SubscribeListChanged> resources,
            Optional<ListChanged> tools,
            Optional<TaskCapabilities> tasks,
            Optional<Map<String, Object>> experimental)
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
            tasks = requireNonNullElse(tasks, Optional.empty());
        }

        public ServerCapabilities()
        {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CompletionCapabilities() {}

    public record LoggingCapabilities() {}

    public record TaskTools(Optional<Property> call)
    {
        public TaskTools
        {
            call = requireNonNullElse(call, Optional.empty());
        }
    }

    public record TaskRequests(Optional<TaskTools> tools)
    {
        public TaskRequests
        {
            tools = requireNonNullElse(tools, Optional.empty());
        }
    }

    public record TaskCapabilities(Optional<Property> cancel, Optional<Property> list, Optional<TaskRequests> requests)
    {
        public TaskCapabilities
        {
            cancel = requireNonNullElse(cancel, Optional.empty());
            list = requireNonNullElse(list, Optional.empty());
            requests = requireNonNullElse(requests, Optional.empty());
        }
    }
}

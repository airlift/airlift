package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

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
        instructions = firstNonNull(instructions, Optional.empty());
    }

    public record ServerCapabilities(
            Optional<CompletionCapabilities> completions,
            Optional<LoggingCapabilities> logging,
            Optional<ListChanged> prompts,
            Optional<SubscribeListChanged> resources,
            Optional<ListChanged> tools,
            Optional<TaskCapabilities> tasks)
    {
        public ServerCapabilities
        {
            completions = firstNonNull(completions, Optional.empty());
            logging = firstNonNull(logging, Optional.empty());
            prompts = firstNonNull(prompts, Optional.empty());
            resources = firstNonNull(resources, Optional.empty());
            tools = firstNonNull(tools, Optional.empty());
            tasks = firstNonNull(tasks, Optional.empty());
        }

        public ServerCapabilities()
        {
            this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CompletionCapabilities() {}

    public record LoggingCapabilities() {}

    public record TaskTools(Optional<Property> call)
    {
        public TaskTools
        {
            call = firstNonNull(call, Optional.empty());
        }
    }

    public record TaskRequests(Optional<TaskTools> tools)
    {
        public TaskRequests
        {
            tools = firstNonNull(tools, Optional.empty());
        }
    }

    public record TaskCapabilities(Optional<Property> cancel, Optional<Property> list, Optional<TaskRequests> requests)
    {
        public TaskCapabilities
        {
            cancel = firstNonNull(cancel, Optional.empty());
            list = firstNonNull(list, Optional.empty());
            requests = firstNonNull(requests, Optional.empty());
        }
    }
}

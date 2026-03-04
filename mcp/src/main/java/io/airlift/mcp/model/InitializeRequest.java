package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record InitializeRequest(
        String protocolVersion,
        ClientCapabilities capabilities,
        Implementation clientInfo,
        Optional<Map<String, Object>> meta)
        implements Meta
{
    public InitializeRequest
    {
        requireNonNull(protocolVersion, "protocolVersion is null");
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(clientInfo, "clientInfo is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public InitializeRequest(String protocolVersion, ClientCapabilities capabilities, Implementation clientInfo)
    {
        this(protocolVersion, capabilities, clientInfo, Optional.empty());
    }

    @Override
    public InitializeRequest withMeta(Map<String, Object> meta)
    {
        return new InitializeRequest(protocolVersion, capabilities, clientInfo, Optional.of(meta));
    }

    public record ClientCapabilities(Optional<ListChanged> roots, Optional<Sampling> sampling, Optional<Elicitation> elicitation, Optional<ClientTaskCapabilities> tasks, Optional<Map<String, Object>> experimental)
            implements Experimental
    {
        public ClientCapabilities
        {
            roots = requireNonNullElse(roots, Optional.empty());
            sampling = requireNonNullElse(sampling, Optional.empty());
            elicitation = requireNonNullElse(elicitation, Optional.empty());
            tasks = requireNonNullElse(tasks, Optional.empty());
            experimental = requireNonNullElse(experimental, Optional.empty());
        }
    }

    public record Sampling() {}

    public record Elicitation() {}

    public record TaskSampling(Optional<Property> createMessage)
    {
        public TaskSampling
        {
            createMessage = requireNonNullElse(createMessage, Optional.empty());
        }
    }

    public record TaskElicitation(Optional<Property> create)
    {
        public TaskElicitation
        {
            create = requireNonNullElse(create, Optional.empty());
        }
    }

    public record ClientTaskRequests(Optional<TaskElicitation> elicitation, Optional<TaskSampling> sampling)
    {
        public ClientTaskRequests
        {
            elicitation = requireNonNullElse(elicitation, Optional.empty());
            sampling = requireNonNullElse(sampling, Optional.empty());
        }
    }

    public record ClientTaskCapabilities(Optional<Property> cancel, Optional<Property> list, Optional<ClientTaskRequests> requests)
    {
        public ClientTaskCapabilities
        {
            cancel = requireNonNullElse(cancel, Optional.empty());
            list = requireNonNullElse(list, Optional.empty());
            requests = requireNonNullElse(requests, Optional.empty());
        }
    }
}

package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

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
        meta = firstNonNull(meta, Optional.empty());
    }

    public InitializeRequest(String protocolVersion, ClientCapabilities capabilities, Implementation clientInfo)
    {
        this(protocolVersion, capabilities, clientInfo, Optional.empty());
    }

    public record ClientCapabilities(Optional<ListChanged> roots, Optional<Sampling> sampling, Optional<Elicitation> elicitation, Optional<ClientTaskCapabilities> tasks)
    {
        public ClientCapabilities
        {
            roots = firstNonNull(roots, Optional.empty());
            sampling = firstNonNull(sampling, Optional.empty());
            elicitation = firstNonNull(elicitation, Optional.empty());
            tasks = firstNonNull(tasks, Optional.empty());
        }
    }

    public record Sampling() {}

    public record Elicitation() {}

    public record TaskSampling(Optional<Property> createMessage)
    {
        public TaskSampling
        {
            createMessage = firstNonNull(createMessage, Optional.empty());
        }
    }

    public record TaskElicitation(Optional<Property> create)
    {
        public TaskElicitation
        {
            create = firstNonNull(create, Optional.empty());
        }
    }

    public record ClientTaskRequests(Optional<TaskElicitation> elicitation, Optional<TaskSampling> sampling)
    {
        public ClientTaskRequests
        {
            elicitation = firstNonNull(elicitation, Optional.empty());
            sampling = firstNonNull(sampling, Optional.empty());
        }
    }

    public record ClientTaskCapabilities(Optional<Property> cancel, Optional<Property> list, Optional<ClientTaskRequests> requests)
    {
        public ClientTaskCapabilities
        {
            cancel = firstNonNull(cancel, Optional.empty());
            list = firstNonNull(list, Optional.empty());
            requests = firstNonNull(requests, Optional.empty());
        }
    }
}

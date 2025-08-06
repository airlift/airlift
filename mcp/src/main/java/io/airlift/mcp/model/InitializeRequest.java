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
    public record ClientCapabilities(Optional<ListChanged> roots, Optional<Sampling> sampling, Optional<Elicitation> elicitation)
    {
        public ClientCapabilities
        {
            requireNonNull(roots, "roots is null");
            requireNonNull(sampling, "sampling is null");
            requireNonNull(elicitation, "elicitation is null");
        }
    }

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

    public record Sampling() {}

    public record Elicitation() {}
}

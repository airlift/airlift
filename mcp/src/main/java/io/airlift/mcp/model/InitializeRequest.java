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
    public record ClientCapabilities(Optional<ListChanged> roots, Optional<Sampling> sampling, Optional<Elicitation> elicitation, Optional<Map<String, Object>> experimental)
            implements Experimental
    {
        public ClientCapabilities
        {
            roots = requireNonNullElse(roots, Optional.empty());
            sampling = requireNonNullElse(sampling, Optional.empty());
            elicitation = requireNonNullElse(elicitation, Optional.empty());
            experimental = requireNonNullElse(experimental, Optional.empty());
        }
    }

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

    public record Sampling() {}

    public record Elicitation() {}
}

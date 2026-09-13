package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record InitializeRequest(
        String protocolVersion,
        ClientCapabilities capabilities,
        Implementation clientInfo,
        Optional<Map<String, Object>> meta)
        implements Meta<InitializeRequest>
{
    public InitializeRequest
    {
        requireNonNull(protocolVersion, "protocolVersion is null");
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(clientInfo, "clientInfo is null");
        meta = normalize(meta);
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

    public record ClientCapabilities(
            Optional<ListChanged> roots,
            Optional<Sampling> sampling,
            Optional<Elicitation> elicitation,
            Optional<Map<String, Object>> extensions,
            Optional<Map<String, Object>> experimental)
            implements Experimental
    {
        public static final ClientCapabilities EMPTY = new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        public ClientCapabilities
        {
            roots = requireNonNullElse(roots, Optional.empty());
            sampling = requireNonNullElse(sampling, Optional.empty());
            elicitation = requireNonNullElse(elicitation, Optional.empty());
            extensions = normalize(extensions);
            experimental = normalize(experimental);
        }

        public static ClientCapabilities requiredExtension(String extension)
        {
            return new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(ImmutableMap.of(extension, ImmutableMap.of())), Optional.empty());
        }

        public boolean hasExtension(String extension)
        {
            return extensions.map(extensions -> extensions.containsKey(extension)).orElse(false);
        }
    }

    public record Sampling() {}

    public record Elicitation() {}
}

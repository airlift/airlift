package io.airlift.mcp.operations;

import io.airlift.mcp.model.Protocol;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record MaxProtocolLevel(Optional<Protocol> maxProtocol)
{
    public MaxProtocolLevel
    {
        requireNonNull(maxProtocol, "maxProtocol is null");
    }

    public Protocol limit(Protocol protocol)
    {
        requireNonNull(protocol, "protocol is null");

        return maxProtocol.map(max -> Protocol.min(max, protocol)).orElse(protocol);
    }
}

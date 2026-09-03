package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record ServerInfo(String serverName, String serverVersion, String instructions)
{
    public ServerInfo
    {
        requireNonNull(serverName, "serverName is null");
        requireNonNull(serverVersion, "serverVersion is null");
        requireNonNull(instructions, "instructions is null");
    }
}

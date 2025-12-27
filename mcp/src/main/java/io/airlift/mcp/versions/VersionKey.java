package io.airlift.mcp.versions;

import static java.util.Objects.requireNonNull;

public record VersionKey(VersionType type, String name)
{
    public VersionKey
    {
        requireNonNull(type, "type is null");
        requireNonNull(name, "name is null");
    }
}

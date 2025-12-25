package io.airlift.mcp.versions;

import static java.util.Objects.requireNonNull;

public record ResourceVersion(String version)
{
    public static final String DEFAULT_VERSION = "";

    public ResourceVersion
    {
        requireNonNull(version, "version is null");
    }
}

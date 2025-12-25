package io.airlift.mcp.versions;

import static java.util.Objects.requireNonNull;

public record SystemListVersions(String toolsVersion, String promptsVersion, String resourcesVersion, String resourceTemplatesVersion)
{
    public static final SystemListVersions DEFAULT = new SystemListVersions("", "", "", "");

    public SystemListVersions
    {
        requireNonNull(toolsVersion, "toolsVersion is null");
        requireNonNull(promptsVersion, "promptsVersion is null");
        requireNonNull(resourcesVersion, "resourcesVersion is null");
        requireNonNull(resourceTemplatesVersion, "resourceTemplatesVersion is null");
    }
}

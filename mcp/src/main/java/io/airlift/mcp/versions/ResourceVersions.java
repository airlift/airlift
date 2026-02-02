package io.airlift.mcp.versions;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.Maps.filterKeys;
import static java.util.Objects.requireNonNull;

public record ResourceVersions(Map<String, String> uriToVersion)
{
    public ResourceVersions
    {
        uriToVersion = ImmutableMap.copyOf(uriToVersion);
    }

    public ResourceVersions with(String uri, String version)
    {
        requireNonNull(uri, "uri is null");
        requireNonNull(version, "version is null");
        Map<String, String> updatedMap = ImmutableMap.<String, String>builderWithExpectedSize(uriToVersion.size() + 1)
                .putAll(uriToVersion)
                .put(uri, version)
                .buildKeepingLast();
        return new ResourceVersions(updatedMap);
    }

    public ResourceVersions without(String uri)
    {
        requireNonNull(uri, "uri is null");
        return new ResourceVersions(filterKeys(uriToVersion, entryUri -> !uri.equals(entryUri)));
    }
}

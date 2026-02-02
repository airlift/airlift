package io.airlift.mcp.versions;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.Maps.filterKeys;

public record ResourceVersions(Map<String, String> uriToVersion)
{
    public ResourceVersions
    {
        uriToVersion = ImmutableMap.copyOf(uriToVersion);
    }

    public ResourceVersions with(String uri, String version)
    {
        Map<String, String> updatedMap = ImmutableMap.<String, String>builder()
                .putAll(uriToVersion)
                .put(uri, version)
                .buildKeepingLast();
        return new ResourceVersions(updatedMap);
    }

    public ResourceVersions without(String uri)
    {
        return new ResourceVersions(filterKeys(uriToVersion, entryUri -> !entryUri.equals(uri)));
    }
}

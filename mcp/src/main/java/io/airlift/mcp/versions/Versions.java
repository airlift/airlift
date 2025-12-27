package io.airlift.mcp.versions;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.mcp.versions.VersionType.SYSTEM;

public record Versions(Map<VersionKey, String> versions)
{
    public static final VersionKey TOOLS_LIST_KEY = new VersionKey(SYSTEM, "tools");
    public static final VersionKey PROMPTS_LIST_KEY = new VersionKey(SYSTEM, "prompts");
    public static final VersionKey RESOURCES_LIST_KEY = new VersionKey(SYSTEM, "resources");

    public static final String DEFAULT_VERSION = "";

    public static final Versions EMPTY = new Versions(ImmutableMap.of(
            TOOLS_LIST_KEY, DEFAULT_VERSION,
            PROMPTS_LIST_KEY, DEFAULT_VERSION,
            RESOURCES_LIST_KEY, DEFAULT_VERSION));

    public Versions
    {
        versions = ImmutableMap.copyOf(versions);
    }

    public Versions withVersion(VersionKey key, String version)
    {
        ImmutableMap.Builder<VersionKey, String> builder = ImmutableMap.builder();
        builder.putAll(versions);
        builder.put(key, version);
        return new Versions(builder.buildKeepingLast());
    }

    public Versions withoutKey(VersionKey key)
    {
        Map<VersionKey, String> updated = versions.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(key))
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Versions(updated);
    }
}

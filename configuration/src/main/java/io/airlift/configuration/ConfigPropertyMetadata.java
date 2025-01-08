package io.airlift.configuration;

public record ConfigPropertyMetadata(String name, boolean securitySensitive)
        implements Comparable<ConfigPropertyMetadata>
{
    @Override
    public int compareTo(ConfigPropertyMetadata that)
    {
        return name.compareTo(that.name);
    }
}

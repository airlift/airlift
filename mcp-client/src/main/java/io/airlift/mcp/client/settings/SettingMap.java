package io.airlift.mcp.client.settings;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public record SettingMap(Map<String, Object> map)
{
    public SettingMap
    {
        map = ImmutableMap.copyOf(map);
    }

    public SettingMap(String key, Object value)
    {
        this(ImmutableMap.of(key, value));
    }

    public SettingMap with(String key, Object value)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder.putAll(map);
        builder.put(key, value);
        return new SettingMap(builder.buildKeepingLast());
    }
}

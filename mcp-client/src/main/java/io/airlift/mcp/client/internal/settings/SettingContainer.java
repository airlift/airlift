package io.airlift.mcp.client.internal.settings;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public class SettingContainer
{
    private final Map<Setting<?>, ?> settings;

    private SettingContainer()
    {
        this(ImmutableMap.of());
    }

    private SettingContainer(Map<Setting<?>, ?> settings)
    {
        this.settings = ImmutableMap.copyOf(settings);
    }

    public static SettingContainer create()
    {
        return new SettingContainer();
    }

    public <V> V getSettingValue(Setting<V> setting)
    {
        Object value = Optional.ofNullable(settings.get(setting))
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + setting));
        return setting.valueType().cast(value);
    }

    public <V> SettingContainer with(Setting<V> setting, V value)
    {
        ImmutableMap.Builder<Setting<?>, Object> builder = ImmutableMap.builder();
        builder.putAll(settings);
        builder.put(setting, value);
        return new SettingContainer(builder.buildKeepingLast());
    }
}

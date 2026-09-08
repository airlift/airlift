package io.airlift.mcp.client.internal.settings;

import static java.util.Objects.requireNonNull;

public abstract class Setting<V>
{
    private final Class<V> valueType;

    protected Setting(Class<V> valueType)
    {
        this.valueType = requireNonNull(valueType, "valueType is null");
    }

    public Class<V> valueType()
    {
        return valueType;
    }
}

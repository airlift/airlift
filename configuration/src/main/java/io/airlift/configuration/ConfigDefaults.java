package io.airlift.configuration;

import com.google.common.collect.ImmutableList;

import java.util.List;

public interface ConfigDefaults<T>
{
    static <T> ConfigDefaults<T> noDefaults()
    {
        return config -> {
        };
    }

    static <T> ConfigDefaults<T> configDefaults(List<? extends ConfigDefaults<T>> configDefaults)
    {
        List<ConfigDefaults<T>> finalConfigDefaults = ImmutableList.copyOf(configDefaults);
        return config -> {
            for (ConfigDefaults<T> configDefault : finalConfigDefaults) {
                configDefault.setDefaults(config);
            }
        };
    }

    void setDefaults(T config);
}

package io.airlift.configuration;

import com.google.inject.Key;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

class ConfigDefaultsHolder<T>
        implements Comparable<ConfigDefaultsHolder<T>>
{
    private static final AtomicLong NEXT_PRIORITY = new AtomicLong();

    private final Key<T> configKey;
    private final ConfigDefaults<T> configDefaults;
    private final long priority = NEXT_PRIORITY.getAndIncrement();

    ConfigDefaultsHolder(Key<T> configKey, ConfigDefaults<T> configDefaults)
    {
        this.configKey = requireNonNull(configKey, "configKey is null");
        this.configDefaults = requireNonNull(configDefaults, "configDefaults is null");
    }

    public Key<T> getConfigKey()
    {
        return configKey;
    }

    public ConfigDefaults<T> getConfigDefaults()
    {
        return configDefaults;
    }

    @Override
    public int compareTo(ConfigDefaultsHolder<T> o)
    {
        return Long.compare(priority, o.priority);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(configDefaults, priority);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConfigDefaultsHolder<?> other = (ConfigDefaultsHolder<?>) obj;
        return Objects.equals(this.configDefaults, other.configDefaults)
                && Objects.equals(this.priority, other.priority);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("configKey", configKey)
                .add("configDefaults", configDefaults)
                .add("priority", priority)
                .toString();
    }
}

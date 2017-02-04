package io.airlift.configuration;

import com.google.inject.Key;

import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public final class ConfigurationBinding<T>
{
    private final Key<T> key;
    private final Class<T> configClass;
    private final Optional<String> prefix;

    public ConfigurationBinding(Key<T> key, Class<T> configClass, Optional<String> prefix)
    {
        requireNonNull(key, "key");
        requireNonNull(configClass, "configClass");
        requireNonNull(prefix, "prefix is null");
        checkArgument(!prefix.isPresent() || !prefix.get().isEmpty(), "prefix is empty");

        this.key = key;
        this.configClass = configClass;
        this.prefix = prefix;
    }

    public Key<T> getKey()
    {
        return key;
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    public Optional<String> getPrefix()
    {
        return prefix;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConfigurationBinding<?> that = (ConfigurationBinding<?>) o;

        if (!key.equals(that.key)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return key.hashCode();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .omitNullValues()
                .add("type", configClass)
                .add("qualifier", Optional.ofNullable(key.getAnnotationType()).map(Class::getSimpleName).orElse(null))
                .add("prefix", prefix.orElse(null))
                .toString();
    }
}

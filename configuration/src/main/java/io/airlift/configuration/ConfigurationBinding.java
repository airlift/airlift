package io.airlift.configuration;

import com.google.inject.Key;

import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record ConfigurationBinding<T>(Key<T> key, Class<T> configClass, Optional<String> prefix)
{
    public ConfigurationBinding
    {
        requireNonNull(key, "key");
        requireNonNull(configClass, "configClass");
        requireNonNull(prefix, "prefix is null");

        checkArgument(prefix.isEmpty() || !prefix.orElseThrow().isEmpty(), "prefix is empty");
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

        return key.equals(that.key);
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

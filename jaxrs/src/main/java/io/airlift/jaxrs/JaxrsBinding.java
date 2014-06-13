package io.airlift.jaxrs;

import com.google.common.base.Function;
import com.google.inject.Key;

import java.util.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

final class JaxrsBinding
{
    private final Key<?> key;

    JaxrsBinding(Key<?> key)
    {
        this.key = checkNotNull(key, "key is null");
    }

    public Key<?> getKey()
    {
        return key;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(key);
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
        JaxrsBinding other = (JaxrsBinding) obj;
        return Objects.equals(this.key, other.key);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("key", key)
                .toString();
    }

    public static Function<JaxrsBinding, Key<?>> keyGetter()
    {
        return new Function<JaxrsBinding, Key<?>>()
        {
            @Override
            public Key<?> apply(JaxrsBinding binding)
            {
                return binding.getKey();
            }
        };
    }
}

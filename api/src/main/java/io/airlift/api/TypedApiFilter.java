package io.airlift.api;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record TypedApiFilter<T>(Optional<T> value)
{
    public TypedApiFilter
    {
        requireNonNull(value, "value is null");
    }
}

package io.airlift.api;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ApiHeader(Optional<String> value)
{
    public ApiHeader
    {
        requireNonNull(value, "value is null");
    }
}

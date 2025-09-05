package io.airlift.api.model;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ModelDeprecation(Method method, String information, Optional<Instant> deprecationDate, Optional<String> newImplementation)
{
    public ModelDeprecation
    {
        requireNonNull(method, "method is null");
        requireNonNull(information, "information is null");
        requireNonNull(deprecationDate, "deprecationDate is null");
        requireNonNull(newImplementation, "newImplementation is null");
    }
}

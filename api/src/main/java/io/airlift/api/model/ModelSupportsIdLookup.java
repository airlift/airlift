package io.airlift.api.model;

import io.airlift.api.ApiId;
import io.airlift.api.validation.ValidatorException;

import static java.util.Objects.requireNonNull;

public record ModelSupportsIdLookup(Class<? extends ApiId<?, ?>> idClass, String prefix)
{
    public ModelSupportsIdLookup
    {
        requireNonNull(idClass, "idClass is null");

        if (requireNonNull(prefix, "prefix is null").isBlank()) {
            throw new ValidatorException("Prefix for %s on %s is blank".formatted(ModelSupportsIdLookup.class.getName(), idClass.getName()));
        }
    }
}

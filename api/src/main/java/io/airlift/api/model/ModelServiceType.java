package io.airlift.api.model;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;

import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelServiceType(String id, int version, String title, String description, Set<ApiServiceTrait> serviceTraits)
{
    public ModelServiceType
    {
        requireNonNull(id, "id is null");
        requireNonNull(title, "title is null");
        requireNonNull(description, "description is null");

        serviceTraits = ImmutableSet.copyOf(serviceTraits);
    }

    public static ModelServiceType map(ApiServiceType apiServiceType)
    {
        return new ModelServiceType(apiServiceType.id(), apiServiceType.version(), apiServiceType.title(), apiServiceType.description(), apiServiceType.traits());
    }
}

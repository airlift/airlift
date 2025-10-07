package io.airlift.api;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static io.airlift.api.ApiServiceTrait.DESCRIPTIONS_REQUIRED;
import static io.airlift.api.ApiServiceTrait.QUOTAS_REQUIRED;
import static io.airlift.api.ApiServiceTrait.REQUIRES_RESOURCE_IDS;
import static io.airlift.api.ApiServiceTrait.USES_VERSIONED_RESOURCES;

public interface ApiServiceType
{
    String id();

    int version();

    String title();

    String description();

    default Set<ApiServiceTrait> traits()
    {
        return ImmutableSet.of(USES_VERSIONED_RESOURCES, REQUIRES_RESOURCE_IDS, QUOTAS_REQUIRED, DESCRIPTIONS_REQUIRED);
    }
}

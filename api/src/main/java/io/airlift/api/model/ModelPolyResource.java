package io.airlift.api.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiOpenApiTrait;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelPolyResource(String key, List<ModelResource> subResources, Set<ApiOpenApiTrait> openApiTraits)
{
    public ModelPolyResource
    {
        requireNonNull(key, "key is null");
        subResources = ImmutableList.copyOf(subResources);
        openApiTraits = ImmutableSet.copyOf(openApiTraits);
    }
}

package io.airlift.api.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record ModelPolyResource(String key, List<ModelResource> subResources)
{
    public ModelPolyResource
    {
        requireNonNull(key, "key is null");
        subResources = ImmutableList.copyOf(subResources);
    }
}

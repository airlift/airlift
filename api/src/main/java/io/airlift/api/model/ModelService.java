package io.airlift.api.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record ModelService(ModelServiceMetadata service, Class<?> serviceClass, List<ModelMethod> methods)
{
    public ModelService
    {
        requireNonNull(service, "service is null");
        requireNonNull(serviceClass, "serviceClass is null");
        methods = ImmutableList.copyOf(methods);
    }
}

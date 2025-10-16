package io.airlift.api.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelApi(ModelServices modelServices, Set<Class<?>> unwrappedResources, Set<Class<?>> polyResources, Set<Class<?>> needsSerializationValidation)
{
    public ModelApi
    {
        requireNonNull(modelServices, "modelServices is null");
        unwrappedResources = ImmutableSet.copyOf(unwrappedResources);
        polyResources = ImmutableSet.copyOf(polyResources);
        needsSerializationValidation = ImmutableSet.copyOf(needsSerializationValidation);
    }

    public ModelApi mergeWith(ModelApi rhs)
    {
        ModelServices mergedModelServices = modelServices.mergeWith(rhs.modelServices());
        Set<Class<?>> mergedUnwrappedResources = Sets.union(unwrappedResources, rhs.unwrappedResources);
        Set<Class<?>> mergedPolyResources = Sets.union(polyResources, rhs.polyResources);
        Set<Class<?>> mergedNeedsSerializationValidation = Sets.union(needsSerializationValidation, rhs.needsSerializationValidation);

        return new ModelApi(mergedModelServices, mergedUnwrappedResources, mergedPolyResources, mergedNeedsSerializationValidation);
    }
}

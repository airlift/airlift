package io.airlift.api.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

public record ModelServices(Set<ModelService> services, Set<ModelResponse> modelResponses, Set<ModelDeprecation> deprecations, Set<String> errors)
{
    public ModelServices
    {
        services = ImmutableSet.copyOf(services);
        modelResponses = ImmutableSet.copyOf(modelResponses);
        deprecations = ImmutableSet.copyOf(deprecations);
        errors = ImmutableSet.copyOf(errors);
    }

    public ModelServices withErrors(Set<String> errors)
    {
        return new ModelServices(services, modelResponses, deprecations, errors);
    }

    public ModelServices mergeWith(ModelServices rhs)
    {
        Set<ModelService> mergedServices = Sets.union(services, rhs.services);
        Set<ModelResponse> mergedModelResponses = Sets.union(modelResponses, rhs.modelResponses);
        Set<ModelDeprecation> mergedDeprecation = Sets.union(deprecations, rhs.deprecations);
        Set<String> mergedErrors = Sets.union(errors, rhs.errors);

        return new ModelServices(mergedServices, mergedModelResponses, mergedDeprecation, mergedErrors);
    }
}

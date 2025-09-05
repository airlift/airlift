package io.airlift.api.validation;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class ResourceValidationState
{
    private final Set<Option> options;
    private final Set<Type> hasBeenValidated;

    public enum Option
    {
        ALLOW_BASIC_RESOURCES,
        PARENT_IS_READ_ONLY,
        ONLY_READ_ONLY_RESOURCES,
        IS_RESULT_RESOURCE,
        ALLOW_BOXED_BASICS,
        ALLOW_POLY_RESOURCES,
    }

    static ResourceValidationState create()
    {
        return new ResourceValidationState(ImmutableSet.of(), new HashSet<>());
    }

    private ResourceValidationState(Set<Option> options, Set<Type> hasBeenValidated)
    {
        this.options = ImmutableSet.copyOf(options);
        this.hasBeenValidated = requireNonNull(hasBeenValidated, "hasBeenValidated is null");   // do not copy
    }

    boolean contains(Option option)
    {
        return options.contains(option);
    }

    ResourceValidationState withOptions(Option... newOptions)
    {
        return new ResourceValidationState(Sets.union(options, ImmutableSet.copyOf(newOptions)), hasBeenValidated);
    }
}

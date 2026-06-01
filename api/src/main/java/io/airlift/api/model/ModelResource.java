package io.airlift.api.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelResource(
        Type type,
        String name,
        Optional<String> openApiName,
        String description,
        List<ModelResource> components,
        ModelResourceType resourceType,
        Type containerType,
        Collection<ModelResourceModifier> modifiers,
        Set<String> quotas,
        Set<String> limitedValues,
        Optional<ModelSupportsIdLookup> supportsIdLookup,
        Optional<ModelPolyResource> polyResource,
        Optional<Map<String, String>> enumDescriptions,
        Optional<ModelResource> jsonValueResource)
{
    public ModelResource
    {
        requireNonNull(name, "name is null");
        requireNonNull(openApiName, "openApiName is null");
        requireNonNull(type, "type is null");
        requireNonNull(description, "description is null");
        requireNonNull(resourceType, "resourceType is null");
        requireNonNull(containerType, "containerType is null");
        requireNonNull(polyResource, "polyResource is null");
        requireNonNull(jsonValueResource, "jsonValueResource is null");

        components = ImmutableList.copyOf(components);
        modifiers = ImmutableSet.copyOf(modifiers);
        quotas = ImmutableSet.copyOf(quotas);
        limitedValues = ImmutableSet.copyOf(limitedValues);
        enumDescriptions = enumDescriptions.map(ImmutableMap::copyOf);
    }

    public ModelResource(Type type, String name, String description, List<ModelResource> components, ModelResourceType resourceType)
    {
        this(type, name, Optional.empty(), description, components, resourceType, type, ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ModelResource(Type type, String name, Optional<String> openApiName, String description, List<ModelResource> components, ModelResourceType resourceType, Collection<ModelResourceModifier> modifiers, Set<String> quotas)
    {
        this(type, name, openApiName, description, components, resourceType, type, modifiers, quotas, ImmutableSet.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ModelResource withNameAndDescription(String name, Optional<String> openApiName, String description)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource asResourceType(ModelResourceType resourceType)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withModifier(ModelResourceModifier modifier)
    {
        Set<ModelResourceModifier> newModifiers = new HashSet<>(modifiers);
        newModifiers.add(modifier);
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, newModifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withModifierRemoved(ModelResourceModifier modifier)
    {
        Set<ModelResourceModifier> newModifiers = new HashSet<>(modifiers);
        newModifiers.remove(modifier);
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, newModifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withContainerType(Type containerType)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withLimitedValues(Set<String> limitedValues)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withSupportsIdLookup(ModelSupportsIdLookup supportsIdLookup)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, Optional.of(supportsIdLookup), polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withPolyResource(ModelPolyResource polyResource)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, Optional.of(polyResource), enumDescriptions, jsonValueResource);
    }

    public ModelResource withEnumDescriptions(Map<String, String> enumDescriptions)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, Optional.of(enumDescriptions), jsonValueResource);
    }

    public ModelResource withComponents(List<ModelResource> components)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, jsonValueResource);
    }

    public ModelResource withJsonValueResource(ModelResource jsonValueResource)
    {
        return new ModelResource(type, name, openApiName, description, components, resourceType, containerType, modifiers, quotas, limitedValues, supportsIdLookup, polyResource, enumDescriptions, Optional.of(jsonValueResource));
    }
}

package io.airlift.api.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.airlift.api.model.ModelResourceModifier.IS_UNWRAPPED;
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
        Optional<ModelResource> streamingEventResource,
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
        requireNonNull(streamingEventResource, "streamingEventResource is null");
        requireNonNull(enumDescriptions, "enumDescriptions is null");
        requireNonNull(jsonValueResource, "jsonValueResource is null");

        components = ImmutableList.copyOf(components);
        modifiers = ImmutableSet.copyOf(modifiers);
        quotas = ImmutableSet.copyOf(quotas);
        limitedValues = ImmutableSet.copyOf(limitedValues);
        enumDescriptions = enumDescriptions.map(ImmutableMap::copyOf);
    }

    public static Builder builder(Type type, String name, String description, ModelResourceType resourceType)
    {
        return new Builder(type, name, description, resourceType);
    }

    public static Builder builder(ModelResource resource)
    {
        return new Builder(resource);
    }

    public ModelResource asContainedResourceType(ModelResourceType resourceType)
    {
        if ((resourceType != ModelResourceType.RESOURCE) && (resourceType != ModelResourceType.LIST)) {
            throw new IllegalArgumentException("Unsupported contained resource type: " + resourceType);
        }

        return builder(this)
                .withResourceType(resourceType)
                .withContainerType(type)
                .build();
    }

    public ModelResource asPatchRequestBody()
    {
        return builder(this)
                .withModifier(ModelResourceModifier.PATCH)
                .build();
    }

    public ModelResource asIdPathParameter(Set<String> limitedValues, Optional<ModelSupportsIdLookup> supportsIdLookup)
    {
        Builder builder = builder(this)
                .withLimitedValues(limitedValues);
        requireNonNull(supportsIdLookup, "supportsIdLookup is null").ifPresent(builder::withSupportsIdLookup);
        return builder.build();
    }

    public ModelResource asPaginatedResult(Type containerType)
    {
        return builder(this)
                .withResourceType(ModelResourceType.PAGINATED_RESULT)
                .withContainerType(containerType)
                .build();
    }

    @VisibleForTesting
    public ModelResource asResourceWithoutUnwrappedComponents()
    {
        ImmutableList.Builder<ModelResource> components = ImmutableList.builder();
        IdentityHashMap<ModelResource, ModelResource> updatedComponents = new IdentityHashMap<>();

        for (ModelResource component : components()) {
            ModelResource updatedComponent = builder(component)
                    .withModifierRemoved(IS_UNWRAPPED)
                    .build();
            components.add(updatedComponent);
            updatedComponents.put(component, updatedComponent);
        }

        Optional<ModelResource> updatedJsonValueResource = jsonValueResource()
                .map(updatedComponents::get);
        Builder builder = builder(this)
                .withComponents(components.build());
        updatedJsonValueResource.ifPresent(builder::withJsonValueResource);
        return builder.build();
    }

    private static Set<ModelResourceModifier> modifiersWith(Collection<ModelResourceModifier> modifiers, ModelResourceModifier modifier)
    {
        Set<ModelResourceModifier> newModifiers = new HashSet<>(modifiers);
        newModifiers.add(requireNonNull(modifier, "modifier is null"));
        return ImmutableSet.copyOf(newModifiers);
    }

    private static Set<ModelResourceModifier> modifiersWithout(Collection<ModelResourceModifier> modifiers, ModelResourceModifier modifier)
    {
        Set<ModelResourceModifier> newModifiers = new HashSet<>(modifiers);
        newModifiers.remove(requireNonNull(modifier, "modifier is null"));
        return ImmutableSet.copyOf(newModifiers);
    }

    public static class Builder
    {
        private Type type;
        private String name;
        private Optional<String> openApiName;
        private String description;
        private List<ModelResource> components;
        private ModelResourceType resourceType;
        private Type containerType;
        private Collection<ModelResourceModifier> modifiers;
        private Set<String> quotas;
        private Set<String> limitedValues;
        private Optional<ModelSupportsIdLookup> supportsIdLookup;
        private Optional<ModelPolyResource> polyResource;
        private Optional<ModelResource> streamingEventResource;
        private Optional<Map<String, String>> enumDescriptions;
        private Optional<ModelResource> jsonValueResource;

        private Builder(Type type, String name, String description, ModelResourceType resourceType)
        {
            this(type,
                    name,
                    Optional.empty(),
                    description,
                    ImmutableList.of(),
                    resourceType,
                    type,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }

        private Builder(ModelResource resource)
        {
            this(requireNonNull(resource, "resource is null").type(),
                    resource.name(),
                    resource.openApiName(),
                    resource.description(),
                    resource.components(),
                    resource.resourceType(),
                    resource.containerType(),
                    resource.modifiers(),
                    resource.quotas(),
                    resource.limitedValues(),
                    resource.supportsIdLookup(),
                    resource.polyResource(),
                    resource.streamingEventResource(),
                    resource.enumDescriptions(),
                    resource.jsonValueResource());
        }

        private Builder(
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
                Optional<ModelResource> streamingEventResource,
                Optional<Map<String, String>> enumDescriptions,
                Optional<ModelResource> jsonValueResource)
        {
            this.type = requireNonNull(type, "type is null");
            this.name = requireNonNull(name, "name is null");
            this.openApiName = requireNonNull(openApiName, "openApiName is null");
            this.description = requireNonNull(description, "description is null");
            this.components = ImmutableList.copyOf(components);
            this.resourceType = requireNonNull(resourceType, "resourceType is null");
            this.containerType = requireNonNull(containerType, "containerType is null");
            this.modifiers = ImmutableSet.copyOf(modifiers);
            this.quotas = ImmutableSet.copyOf(quotas);
            this.limitedValues = ImmutableSet.copyOf(limitedValues);
            this.supportsIdLookup = requireNonNull(supportsIdLookup, "supportsIdLookup is null");
            this.polyResource = requireNonNull(polyResource, "polyResource is null");
            this.streamingEventResource = requireNonNull(streamingEventResource, "streamingEventResource is null");
            this.enumDescriptions = requireNonNull(enumDescriptions, "enumDescriptions is null").map(ImmutableMap::copyOf);
            this.jsonValueResource = requireNonNull(jsonValueResource, "jsonValueResource is null");
        }

        public Builder withNameAndDescription(String name, Optional<String> openApiName, String description)
        {
            this.name = requireNonNull(name, "name is null");
            this.openApiName = requireNonNull(openApiName, "openApiName is null");
            this.description = requireNonNull(description, "description is null");
            return this;
        }

        public Builder withOpenApiName(Optional<String> openApiName)
        {
            this.openApiName = requireNonNull(openApiName, "openApiName is null");
            return this;
        }

        public Builder withComponents(List<ModelResource> components)
        {
            this.components = ImmutableList.copyOf(components);
            return this;
        }

        public Builder withResourceType(ModelResourceType resourceType)
        {
            this.resourceType = requireNonNull(resourceType, "resourceType is null");
            return this;
        }

        public Builder withContainerType(Type containerType)
        {
            this.containerType = requireNonNull(containerType, "containerType is null");
            return this;
        }

        public Builder withModifiers(Collection<ModelResourceModifier> modifiers)
        {
            this.modifiers = ImmutableSet.copyOf(modifiers);
            return this;
        }

        public Builder withModifier(ModelResourceModifier modifier)
        {
            this.modifiers = modifiersWith(modifiers, modifier);
            return this;
        }

        public Builder withModifierRemoved(ModelResourceModifier modifier)
        {
            this.modifiers = modifiersWithout(modifiers, modifier);
            return this;
        }

        public Builder withQuotas(Set<String> quotas)
        {
            this.quotas = ImmutableSet.copyOf(quotas);
            return this;
        }

        public Builder withLimitedValues(Set<String> limitedValues)
        {
            this.limitedValues = ImmutableSet.copyOf(limitedValues);
            return this;
        }

        public Builder withSupportsIdLookup(ModelSupportsIdLookup supportsIdLookup)
        {
            this.supportsIdLookup = Optional.of(requireNonNull(supportsIdLookup, "supportsIdLookup is null"));
            return this;
        }

        public Builder withPolyResource(ModelPolyResource polyResource)
        {
            this.polyResource = Optional.of(requireNonNull(polyResource, "polyResource is null"));
            return this;
        }

        public Builder withStreamingEventResource(ModelResource streamingEventResource)
        {
            this.streamingEventResource = Optional.of(requireNonNull(streamingEventResource, "streamingEventResource is null"));
            return this;
        }

        public Builder withEnumDescriptions(Map<String, String> enumDescriptions)
        {
            this.enumDescriptions = Optional.of(ImmutableMap.copyOf(enumDescriptions));
            return this;
        }

        public Builder withJsonValueResource(ModelResource jsonValueResource)
        {
            this.jsonValueResource = Optional.of(requireNonNull(jsonValueResource, "jsonValueResource is null"));
            return this;
        }

        public ModelResource build()
        {
            return new ModelResource(
                    type,
                    name,
                    openApiName,
                    description,
                    components,
                    resourceType,
                    containerType,
                    modifiers,
                    quotas,
                    limitedValues,
                    supportsIdLookup,
                    polyResource,
                    streamingEventResource,
                    enumDescriptions,
                    jsonValueResource);
        }
    }
}

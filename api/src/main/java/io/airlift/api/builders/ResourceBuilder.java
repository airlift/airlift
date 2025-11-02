package io.airlift.api.builders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiId;
import io.airlift.api.ApiMultiPart;
import io.airlift.api.ApiMultiPart.ApiMultiPartFormWithResource;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiResponse;
import io.airlift.api.ApiStreamResponse;
import io.airlift.api.ApiUnwrapped;
import io.airlift.api.internals.Mappers;
import io.airlift.api.model.ModelPolyResource;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelResourceType;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.api.internals.Generics.extractGenericParameter;
import static io.airlift.api.internals.Generics.typeResolver;
import static io.airlift.api.internals.Mappers.openApiName;
import static io.airlift.api.model.ModelResourceModifier.HAS_RESOURCE_ID;
import static io.airlift.api.model.ModelResourceModifier.HAS_VERSION;
import static io.airlift.api.model.ModelResourceModifier.IS_MULTIPART_FORM;
import static io.airlift.api.model.ModelResourceModifier.IS_STREAMING_RESPONSE;
import static io.airlift.api.model.ModelResourceModifier.IS_UNWRAPPED;
import static io.airlift.api.model.ModelResourceModifier.MULTIPART_RESOURCE_IS_FIRST_ITEM;
import static io.airlift.api.model.ModelResourceModifier.OPTIONAL;
import static io.airlift.api.model.ModelResourceModifier.READ_ONLY;
import static io.airlift.api.model.ModelResourceModifier.RECURSIVE_REFERENCE;
import static io.airlift.api.model.ModelResourceModifier.TOP_LEVEL_READ_ONLY;
import static io.airlift.api.model.ModelResourceType.RESOURCE;
import static io.airlift.api.validation.ValidationContext.isForcedReadOnly;
import static java.util.Objects.requireNonNull;

public class ResourceBuilder
{
    private static final Map<Class<?>, Class<?>> supportedBoxedResourceTypes = ImmutableMap.of(
            Boolean.class, boolean.class,
            Integer.class, int.class,
            Long.class, long.class,
            Double.class, double.class);

    private static final Set<Class<?>> supportedBasicResourceTypes = ImmutableSet.of(
            boolean.class,
            int.class,
            long.class,
            double.class,
            String.class,
            Instant.class,
            BigDecimal.class,
            LocalDate.class,
            Enum.class,
            UUID.class,
            ApiId.class,
            ApiResourceVersion.class);

    private static final Map<Class<?>, String> definedDescriptions = ImmutableMap.of(ApiResourceVersion.class, """
            Used to ensure consistency for resource updates. A syncToken that is returned
            from the server is valid until the resource is updated when a new syncToken will
            be generated. Only the latest version of the object is maintained.
            """);

    private final Type resource;
    private final Map<Type, ModelResource> builtResources;
    private final RecursionChecker recursionChecker;

    private ResourceBuilder(Type resource, Map<Type, ModelResource> builtResources, RecursionChecker recursionChecker)
    {
        this.resource = requireNonNull(resource, "resource is null");
        this.builtResources = requireNonNull(builtResources, "validatedResources is null"); // do not copy
        this.recursionChecker = requireNonNull(recursionChecker, "recursionChecker is null");
    }

    public static ResourceBuilder resourceBuilder(Type resource)
    {
        return new ResourceBuilder(resource, new HashMap<>(), new RecursionChecker());
    }

    public static ResourceBuilder resourceBuilder(Type resource, ResourceBuilder from)
    {
        return new ResourceBuilder(resource, from.builtResources, from.recursionChecker);
    }

    public ResourceBuilder toBuilder(Type resource)
    {
        return new ResourceBuilder(resource, builtResources, recursionChecker);
    }

    public ModelResource build()
    {
        return internalBuildAndAdd(Optional.empty(), resource);
    }

    private ModelResource internalBuildAndAdd(Optional<String> componentName, Type resource)
    {
        ModelResource modelResource = internalBuild(componentName, resource);
        builtResources.put(resource, modelResource);
        return modelResource;
    }

    public static boolean isBasic(Class<?> type, boolean allowBoxedBasics)
    {
        if (allowBoxedBasics && supportedBoxedResourceTypes.keySet().stream().anyMatch(clazz -> clazz.isAssignableFrom(type))) {
            return true;
        }
        return supportedBasicResourceTypes.stream().anyMatch(clazz -> clazz.isAssignableFrom(type));
    }

    private ModelResource internalBuild(Optional<String> componentName, Type resource)
    {
        ModelResource existingModelResource = builtResources.get(resource);    // can't use computeIfAbsent() as this method is called recursively
        if (existingModelResource != null) {
            return existingModelResource;
        }

        TypeToken<?> typeToken = TypeToken.of(typeResolver.resolveType(resource));

        if (typeToken.isSubtypeOf(Optional.class)) {
            return internalBuildAndAdd(componentName, extractGenericParameter(typeToken.getType(), 0))
                    .withModifier(OPTIONAL)
                    .withContainerType(resource);
        }

        if (typeToken.isSubtypeOf(Collection.class)) {
            Type targetType = extractGenericParameter(typeToken.getType(), 0);

            recursionChecker.pushRecursionAllowed(true);
            try {
                return internalBuildAndAdd(componentName, targetType)
                        .asResourceType(ModelResourceType.LIST)
                        .withContainerType(resource);
            }
            finally {
                recursionChecker.popRecursionAllowed();
            }
        }

        if (typeToken.isSubtypeOf(Map.class)) {
            return new ModelResource(String.class, String.class.getSimpleName(), "n/a", ImmutableList.of(), ModelResourceType.MAP).withContainerType(resource);
        }

        if ((typeToken.getType() instanceof Class<?> clazz) && isBasic(clazz, true)) {
            Class<?> unboxed = unboxIfNeeded(clazz);
            ModelResource modelResource = new ModelResource(unboxed, unboxed.getSimpleName(), "n/a", ImmutableList.of(), ModelResourceType.BASIC);
            if (unboxed.isEnum()) {
                Map<String, String> descriptions = enumDescriptions(unboxed);
                if (!descriptions.isEmpty()) {
                    modelResource = modelResource.withEnumDescriptions(descriptions);
                }
            }
            return modelResource;
        }

        if (typeToken.isSubtypeOf(ApiStreamResponse.class)) {
            return internalBuildAndAdd(componentName, extractGenericParameter(typeToken.getType(), 0))
                    .withContainerType(typeToken.getType())
                    .withModifier(IS_STREAMING_RESPONSE)
                    .withModifier(HAS_RESOURCE_ID)
                    .withModifier(READ_ONLY)
                    .withModifier(HAS_VERSION);
        }

        if (typeToken.isSubtypeOf(ApiMultiPart.class)) {
            Type multiPartResourceType = extractGenericParameter(typeToken.getType(), 0);

            ModelResource modelResource = internalBuildAndAdd(componentName, multiPartResourceType);
            if (typeToken.isSubtypeOf(ApiMultiPartFormWithResource.class)) {
                modelResource = modelResource.withModifier(MULTIPART_RESOURCE_IS_FIRST_ITEM);
            }

            return modelResource
                    .withModifier(IS_MULTIPART_FORM)
                    .withContainerType(resource);
        }

        if ((typeToken.getType() instanceof Class<?> clazz) && clazz.isRecord()) {
            return buildResourceRecordFromAnnotation(typeToken, clazz);
        }

        if ((typeToken.getType() instanceof Class<?> clazz) && clazz.isInterface() && clazz.isSealed()) {
            ApiPolyResource apiPolyResource = clazz.getAnnotation(ApiPolyResource.class);
            if (apiPolyResource == null) {
                throw new ValidatorException("%s is missing @%s annotation".formatted(typeToken.getType(), ApiPolyResource.class.getSimpleName()));
            }

            return buildPolyResource(componentName, apiPolyResource, clazz);
        }

        return throwInvalid(typeToken.getType(), componentName);
    }

    private ModelResource buildResourceRecordFromAnnotation(TypeToken<?> typeToken, Class<?> clazz)
    {
        String name;
        Optional<String> openApiName;
        String description;
        Set<String> quotas;

        ApiResource apiResource = clazz.getAnnotation(ApiResource.class);
        if (apiResource != null) {
            name = apiResource.name();
            openApiName = openApiName(apiResource.openApiAlternateName());
            description = apiResource.description();
            quotas = ImmutableSet.copyOf(apiResource.quotas());
        }
        else {
            ApiResponse apiResponse = clazz.getAnnotation(ApiResponse.class);
            if (apiResponse == null) {
                throw new ValidatorException("%s is missing @%s or %s annotation".formatted(typeToken.getType(), ApiResource.class.getSimpleName(), ApiResponse.class.getSimpleName()));
            }

            name = apiResponse.name();
            openApiName = openApiName(apiResponse.openApiAlternateName());
            description = apiResponse.description();
            quotas = ImmutableSet.of();
        }

        return buildResourceRecord(typeToken.getType(), clazz, name, openApiName, description, quotas);
    }

    private ModelResource buildResourceRecord(Type type, Class<?> clazz, String name, Optional<String> openApiName, String description, Set<String> quotas)
    {
        if (recursionChecker.isActiveValidatingResource(type)) {
            if (!recursionChecker.recursionAllowed()) {
                throw new ValidatorException("Recursive resources are only allowed in collections");
            }
            return new ModelResource(type, name, openApiName, description, ImmutableList.of(), RESOURCE, ImmutableSet.of(RECURSIVE_REFERENCE), quotas);
        }

        List<ModelResource> components = new ArrayList<>();
        Collection<ModelResourceModifier> modifiers = new HashSet<>();
        if (clazz.getAnnotation(ApiReadOnly.class) != null) {
            modifiers.add(TOP_LEVEL_READ_ONLY);
        }

        // remember the parent resource so we can detect recursive resources
        recursionChecker.addValidatingResources(type);
        recursionChecker.pushRecursionAllowed(false);
        try {
            for (RecordComponent recordComponent : clazz.getRecordComponents()) {
                boolean isVersion = ApiResourceVersion.class.isAssignableFrom(recordComponent.getType());
                if (isVersion) {
                    modifiers.add(HAS_VERSION);
                }
                if (ApiId.class.isAssignableFrom(recordComponent.getType())) {
                    String ourResourceName = Mappers.buildResourceName(clazz);
                    if (ourResourceName.equals(Mappers.buildResourceName(Mappers.resourceFromPossibleId(recordComponent.getType())))) {
                        modifiers.add(HAS_RESOURCE_ID);
                    }
                }

                ApiDescription componentDescription = recordComponent.getAnnotation(ApiDescription.class);
                String forcedDescription = definedDescriptions.get(recordComponent.getType());
                boolean isUnwrapped = recordComponent.isAnnotationPresent(ApiUnwrapped.class);

                String appliedDescription = (forcedDescription != null) ? forcedDescription : Optional.ofNullable(componentDescription).map(ApiDescription::value).orElse("");

                ModelResource componentModelResource = internalBuildAndAdd(Optional.of(recordComponent.getName()), recordComponent.getGenericType());
                // the component resource name will not be accurate as it gets set to this record's component name, so set the openApiName appropriately
                String componentOpenApiName = componentModelResource.openApiName().orElseGet(componentModelResource::name);
                ModelResource component = componentModelResource.withNameAndDescription(recordComponent.getName(), Optional.of(componentOpenApiName), appliedDescription);

                boolean isForcedReadOnly = isForcedReadOnly(recordComponent.getType());
                boolean hasReadOnly = (recordComponent.getAnnotation(ApiReadOnly.class) != null);
                if (hasReadOnly || isForcedReadOnly) {
                    component = component.withModifier(READ_ONLY);
                }
                if (isUnwrapped) {
                    component = component.withModifier(IS_UNWRAPPED);
                }

                components.add(component);
            }
        }
        finally {
            recursionChecker.removeValidatingResources(type);
            recursionChecker.popRecursionAllowed();
        }

        return new ModelResource(clazz, name, openApiName, description, components, RESOURCE, modifiers, quotas);
    }

    private ModelResource buildPolyResource(Optional<String> componentName, ApiPolyResource apiPolyResource, Class<?> clazz)
    {
        List<ModelResource> subResources = Stream.of(clazz.getPermittedSubclasses())
                .map(subResourceClass -> internalBuildAndAdd(componentName, subResourceClass))
                .collect(toImmutableList());

        return new ModelResource(clazz, apiPolyResource.name(), openApiName(apiPolyResource.openApiAlternateName()), apiPolyResource.description(), ImmutableList.of(), RESOURCE, ImmutableSet.of(), ImmutableSet.copyOf(apiPolyResource.quotas()))
                .withPolyResource(new ModelPolyResource(apiPolyResource.key(), subResources));
    }

    private ModelResource throwInvalid(Type type, Optional<String> name)
    {
        if (name.isPresent()) {
            throw new ValidatorException("\"%s %s\" is not a valid resource type".formatted(type, name.get()));
        }
        throw new ValidatorException("%s is not a valid resource type".formatted(type));
    }

    private Map<String, String> enumDescriptions(Class<?> clazz)
    {
        return Stream.of(clazz.getFields())
                .filter(Field::isEnumConstant)
                .flatMap(field -> {
                    ApiDescription descriptionAnnotation = field.getAnnotation(ApiDescription.class);
                    return Optional.ofNullable(descriptionAnnotation).map(description -> Map.entry(field.getName(), description.value())).stream();
                })
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Class<?> unboxIfNeeded(Class<?> clazz)
    {
        return supportedBoxedResourceTypes.getOrDefault(clazz, clazz);
    }
}

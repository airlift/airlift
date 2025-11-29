package io.airlift.api.validation;

import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiId;
import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiStreamResponse;
import io.airlift.api.ApiUnwrapped;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelPolyResource;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceType;
import io.airlift.api.model.ModelServiceMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.airlift.api.ApiResourceVersion.PUBLIC_NAME;
import static io.airlift.api.ApiServiceTrait.DESCRIPTIONS_REQUIRED;
import static io.airlift.api.binding.JaxrsUtil.isApiResource;
import static io.airlift.api.builders.ResourceBuilder.isBasic;
import static io.airlift.api.internals.Generics.extractGenericParameter;
import static io.airlift.api.internals.Generics.validateMap;
import static io.airlift.api.internals.Mappers.buildResourceId;
import static io.airlift.api.internals.Mappers.resourceFromPossibleId;
import static io.airlift.api.internals.Strings.capitalize;
import static io.airlift.api.model.ModelResourceModifier.IS_MULTIPART_FORM;
import static io.airlift.api.model.ModelResourceModifier.IS_STREAMING_RESPONSE;
import static io.airlift.api.model.ModelResourceModifier.IS_UNWRAPPED;
import static io.airlift.api.model.ModelResourceModifier.OPTIONAL;
import static io.airlift.api.model.ModelResourceModifier.PATCH;
import static io.airlift.api.model.ModelResourceModifier.READ_ONLY;
import static io.airlift.api.model.ModelResourceModifier.VOID;
import static io.airlift.api.model.ModelResourceModifier.hasReadOnly;
import static io.airlift.api.model.ModelResourceType.LIST;
import static io.airlift.api.model.ModelResourceType.RESOURCE;
import static io.airlift.api.validation.ResourceValidationState.Option.ALLOW_BASIC_RESOURCES;
import static io.airlift.api.validation.ResourceValidationState.Option.ALLOW_BOXED_BASICS;
import static io.airlift.api.validation.ResourceValidationState.Option.ALLOW_POLY_RESOURCES;
import static io.airlift.api.validation.ResourceValidationState.Option.IS_RESULT_RESOURCE;
import static io.airlift.api.validation.ResourceValidationState.Option.PARENT_IS_READ_ONLY;
import static io.airlift.api.validation.ValidationContext.NameType.ENUM;
import static io.airlift.api.validation.ValidationContext.NameType.STANDARD;
import static io.airlift.api.validation.ValidationContext.isForcedReadOnly;

public interface ResourceValidator
{
    static void validateResource(ValidationContext context, ModelServiceMetadata service, ModelResource modelResource)
    {
        context.inContext("Resource %s".formatted(modelResource.type().getTypeName()),
                subContext -> internalValidate(subContext, service, Optional.empty(), modelResource, ResourceValidationState.create()));
    }

    static void validateRequestBody(ValidationContext context, ModelServiceMetadata service, ModelMethod modelMethod, ModelResource modelResource)
    {
        context.inContext("Method: %s, request body %s".formatted(modelMethod.method(), modelResource.type().getTypeName()),
                subContext -> {
                    if (modelResource.modifiers().contains(PATCH)) {
                        validatePatch(modelMethod.method(), modelResource);
                    }
                    internalValidate(subContext, service, Optional.empty(), modelResource, ResourceValidationState.create());
                });
    }

    static void validateParameter(ValidationContext context, ModelServiceMetadata service, ModelMethod modelMethod, String name, ModelResource modelResource)
    {
        context.inContext("Method: %s, parameter %s, resource: %s".formatted(modelMethod.method(), name, modelResource.type().getTypeName()),
                subContext -> internalValidate(subContext, service, Optional.of(name), modelResource, ResourceValidationState.create().withOptions(ALLOW_BASIC_RESOURCES)));
    }

    static void validateResult(ValidationContext context, ModelServiceMetadata service, ModelMethod modelMethod)
    {
        context.inContext("Resource %s".formatted(modelMethod.returnType().type().getTypeName()),
                subContext -> internalValidate(subContext, service, Optional.empty(), modelMethod.returnType(), ResourceValidationState.create().withOptions(IS_RESULT_RESOURCE)));
    }

    private static void internalValidate(ValidationContext context, ModelServiceMetadata service, Optional<String> componentName, ModelResource modelResource, ResourceValidationState state)
    {
        if (context.resourceHasBeenValidated(modelResource.type())) {
            return;
        }
        context.markResourceAsValidated(modelResource.type());

        if ((modelResource.type() instanceof Class<?> clazz) && clazz.isRecord()) {
            context.registerNeedsSerialization(clazz);
        }

        if (!state.contains(ALLOW_BASIC_RESOURCES) && (modelResource.type() instanceof Class<?> clazz) && isBasic(clazz, state.contains(ALLOW_BOXED_BASICS))) {
            throwInvalid(modelResource.type(), componentName);
        }

        switch (modelResource.resourceType()) {
            case LIST -> {
                if (!state.contains(PARENT_IS_READ_ONLY) && !state.contains(IS_RESULT_RESOURCE)) {
                    Type targetType = modelResource.type();
                    if (!(targetType instanceof Class<?> clazz) || (!String.class.isAssignableFrom(clazz) && !ApiId.class.isAssignableFrom(clazz) && !clazz.isEnum() && !clazz.isAnnotationPresent(ApiResource.class) && !clazz.isAnnotationPresent(ApiPolyResource.class))) {
                        throw new ValidatorException("Collections that are not read only must be Collection<String> or Collection<? extends ApiAbstractId> or Collection<? extends Enum> or a collection of resources. %s is not".formatted(targetType));
                    }
                }

                if (modelResource.modifiers().contains(IS_MULTIPART_FORM)) {
                    throw new ValidatorException("%s cannot be used in collections".formatted(ApiMultiPartForm.class));
                }
                if (modelResource.modifiers().contains(IS_STREAMING_RESPONSE)) {
                    throw new ValidatorException("%s cannot be used in collections".formatted(ApiStreamResponse.class));
                }

                if (isApiResource(modelResource.type())) {
                    validateDeclaredResource(context, service, modelResource, state);
                }
            }

            case MAP -> {
                if (modelResource.modifiers().contains(OPTIONAL)) {
                    Type type = extractGenericParameter(modelResource.containerType(), 0);
                    validateMap(type);
                }
                else {
                    validateMap(modelResource.containerType());
                }
            }

            case BASIC -> {
                if (!state.contains(ALLOW_BASIC_RESOURCES)) {
                    throw new ValidatorException("Basic types not allowed here. Name: %s".formatted(modelResource.name()));
                }
            }

            case PAGINATED_RESULT -> {
                if (!state.contains(IS_RESULT_RESOURCE)) {
                    throw new ValidatorException("%s cannot be a parameter".formatted(ApiPagination.class.getSimpleName()));
                }

                if (isApiResource(modelResource.type())) {
                    validateDeclaredResource(context, service, modelResource, state);
                }
            }

            case RESOURCE -> validateDeclaredResource(context, service, modelResource, state);
        }
    }

    private static void validateDeclaredResource(ValidationContext context, ModelServiceMetadata service, ModelResource modelResource, ResourceValidationState state)
    {
        if (!modelResource.modifiers().contains(VOID)) {
            context.validateName(modelResource.name(), STANDARD);
        }
        context.validateOpenApiName(modelResource);

        if (modelResource.modifiers().contains(IS_STREAMING_RESPONSE) && !state.contains(IS_RESULT_RESOURCE)) {
            throw new ValidatorException("%s cannot be a parameter".formatted(ApiStreamResponse.class.getSimpleName()));
        }

        if (modelResource.modifiers().contains(IS_MULTIPART_FORM)) {
            if (state.contains(IS_RESULT_RESOURCE)) {
                throw new ValidatorException("%s can only be parameters".formatted(ApiMultiPartForm.class.getSimpleName()));
            }
            if (modelResource.polyResource().isPresent()) {
                throw new ValidatorException("%s cannot be used with @%s".formatted(ApiMultiPartForm.class.getSimpleName(), ApiPolyResource.class.getSimpleName()));
            }
        }

        modelResource.polyResource().ifPresent(polyResource -> validatePolyResource(context, modelResource, polyResource));

        modelResource.components().forEach(component -> {
            boolean hasDescription = !component.description().isBlank();

            if (component.modifiers().contains(IS_UNWRAPPED)) {
                if (hasDescription) {
                    throw new ValidatorException("%s fields cannot have a %s. At: %s".formatted(ApiUnwrapped.class.getSimpleName(), ApiDescription.class.getSimpleName(), component.type()));
                }
            }
            else if ((component.resourceType() != ModelResourceType.BASIC) && !hasDescription && service.type().serviceTraits().contains(DESCRIPTIONS_REQUIRED)) {
                throw new ValidatorException("%s component %s is missing %s annotation.".formatted(modelResource.type(), component.name(), ApiDescription.class.getSimpleName()));
            }

            validateRecordComponentNaming(context, modelResource, component);

            ResourceValidationState nextState = state.withOptions(ALLOW_BASIC_RESOURCES, ALLOW_POLY_RESOURCES);
            if (component.modifiers().contains(READ_ONLY)) {
                nextState = nextState.withOptions(PARENT_IS_READ_ONLY);
            }

            internalValidate(context, service, Optional.of(component.name()), component, nextState);

            boolean isForcedReadOnly = isForcedReadOnly(component.type());
            boolean hasReadOnly = component.modifiers().contains(READ_ONLY);
            boolean isUnwrapped = component.modifiers().contains(IS_UNWRAPPED);

            if (isForcedReadOnly && hasReadOnly && (component.resourceType() == RESOURCE)) {
                throw new ValidatorException("%s fields cannot be read only in %s".formatted(component.type(), modelResource.type()));
            }

            if (isUnwrapped) {
                context.registerResourcesWithUnwrappedComponents(TypeToken.of(modelResource.type()).getRawType());
            }
        });
    }

    private static void validatePatch(Method method, ModelResource requestBodyResource)
    {
        long count = Stream.of(method.getParameterTypes()).filter(ApiPatch.class::isAssignableFrom).count();
        if (count > 1) {
            throw new ValidatorException("Method has multiple %s parameters".formatted(ApiPatch.class.getSimpleName()));
        }

/*
TODO why this?
        if (isPartialPatch) {
            return;
        }
*/

        if (!canBePatched(requestBodyResource, new HashMap<>())) {
            throw new ValidatorException("%s is or contains resources that cannot be patched".formatted(requestBodyResource.type()));
        }
    }

    private static boolean canBePatched(ModelResource resource, Map<ModelResource, Boolean> cache)
    {
        Boolean cached = cache.get(resource);
        if (cached != null) {
            return cached;
        }

        if ((resource.resourceType() == ModelResourceType.BASIC) || hasReadOnly(resource.modifiers())) {
            cache.put(resource, true);
            return true;
        }

        if (resource.components().stream().allMatch(component -> canBePatched(component, cache))) {
            cache.put(resource, true);
            return true;
        }

        cache.put(resource, false);
        return false;
    }

    private static void validateRecordComponentNaming(ValidationContext context, ModelResource modelResource, ModelResource component)
    {
        boolean isCollection = component.resourceType() == LIST;
        boolean isOptional = component.modifiers().contains(OPTIONAL);

        Class<?> rawType = TypeToken.of(component.type()).getRawType();

        context.validateName(component.name(), STANDARD);

        if (rawType.isEnum()) {
            context.inContext("For enumeration: " + rawType.getSimpleName(), subContext ->
                    Stream.of(rawType.getEnumConstants()).forEach(e -> subContext.validateName(e.toString(), ENUM)));
        }

        if (ApiResourceVersion.class.isAssignableFrom(rawType)) {
            validateRequiredName(modelResource, component, rawType, PUBLIC_NAME);
        }

        else if (ApiId.class.isAssignableFrom(rawType)) {
            String id = buildResourceId(resourceFromPossibleId(rawType));
            if (isCollection || isOptional) {
                String idSuffix = isCollection ? (id + "s") : id;
                String capitalizedIdSuffix = capitalize(idSuffix);
                if (!component.name().endsWith(capitalizedIdSuffix) && !component.name().equals(idSuffix)) {
                    String container = isOptional ? "Optionals" : "Collections";
                    throw new ValidatorException("%s field names in %s must end with \"%s\" or be named \"%s\". At %s".formatted(rawType.getSimpleName(), container, capitalizedIdSuffix, idSuffix, modelResource.type()));
                }
            }
            else {
                validateRequiredName(modelResource, component, rawType, id);
            }
        }
    }

    private static void validateRequiredName(ModelResource modelResource, ModelResource component, Class<?> rawType, String requiredName)
    {
        if (!requiredName.equals(component.name())) {
            throw new ValidatorException("%s fields must be named \"%s\". At %s".formatted(rawType.getSimpleName(), requiredName, modelResource.type()));
        }
    }

    private static void validatePolyResource(ValidationContext context, ModelResource modelResource, ModelPolyResource polyResource)
    {
        if (modelResource.type() instanceof Class<?> clazz) {
            context.registerPolyResource(clazz);
        }
        else {
            throw new ValidatorException("Only classes can be polymorphic resources. Found: %s".formatted(modelResource.type()));
        }

        polyResource.subResources()
                .forEach(subResource -> subResource.components()
                        .forEach(component -> {
                            if (component.name().equals(polyResource.key())) {
                                throw new ValidatorException("%s is a sub-resource of %s and has a component that is the same as the %s key: %s".formatted(subResource.type(), ApiPolyResource.class.getSimpleName(), ApiPolyResource.class.getSimpleName(), polyResource.key()));
                            }
                        }));
    }

    private static void throwInvalid(Type type, Optional<String> name)
    {
        if (name.isPresent()) {
            throw new ValidatorException("\"%s %s\" is not a valid resource type".formatted(type, name.get()));
        }
        throw new ValidatorException("%s is not a valid resource type".formatted(type));
    }
}

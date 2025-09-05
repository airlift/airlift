package io.airlift.api.validation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiId;
import io.airlift.api.ApiModifier;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiType;
import io.airlift.api.ApiValidateOnly;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelOptionalParameter;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelResourceType;
import io.airlift.log.Logger;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static io.airlift.api.builders.MethodBuilder.OPTIONAL_PARAMETER_MAP;
import static io.airlift.api.model.ModelOptionalParameter.Metadata.MULTIPLE_ALLOWED;
import static io.airlift.api.model.ModelOptionalParameter.Metadata.REQUIRES_ALLOWED_VALUES;
import static io.airlift.api.model.ModelResourceModifier.HAS_RESOURCE_ID;
import static io.airlift.api.model.ModelResourceModifier.HAS_VERSION;
import static io.airlift.api.model.ModelResourceModifier.PATCH;
import static java.util.function.Predicate.not;

public interface MethodValidator
{
    Logger log = Logger.get(MethodValidator.class);

    Map<ApiType, Collection<Class<?>>> ALLOWED_PARAMETER_TYPES = ImmutableMap.of(
            ApiType.GET, ImmutableSet.of(ApiFilter.class, ApiFilterList.class, ApiModifier.class, ApiHeader.class/*, ApiOrderBy.class*/),
            ApiType.LIST, ImmutableSet.of(ApiPagination.class, ApiFilter.class, ApiFilterList.class, ApiModifier.class, ApiHeader.class, ApiOrderBy.class),
            ApiType.CREATE, ImmutableSet.of(ApiValidateOnly.class, ApiModifier.class, ApiHeader.class),
            ApiType.UPDATE, ImmutableSet.of(ApiValidateOnly.class, ApiFilter.class, ApiFilterList.class, ApiModifier.class, ApiHeader.class, ApiOrderBy.class),
            ApiType.DELETE, ImmutableSet.of(ApiValidateOnly.class, ApiFilter.class, ApiFilterList.class, ApiModifier.class, ApiHeader.class));

    static void validateMethod(ValidationContext validationContext, ModelMethod modelMethod, Set<ApiServiceTrait> serviceTraits)
    {
        validationContext.inContext("Method %s".formatted(modelMethod.method()), context -> internalValidateMethod(context, modelMethod, serviceTraits));
    }

    private static void internalValidateMethod(ValidationContext validationContext, ModelMethod modelMethod, Set<ApiServiceTrait> serviceTraits)
    {
        if (hasJaxRsAnnotation(modelMethod.method().getDeclaredAnnotations(), false)) {
            throw new ValidatorException("Method %s has JAX-RS annotations".formatted(modelMethod.method()));
        }

        if (modelMethod.methodType() == ApiType.LIST) {
            if ((modelMethod.returnType().resourceType() != ModelResourceType.LIST) && (modelMethod.returnType().resourceType() != ModelResourceType.PAGINATED_RESULT)) {
                throw new ValidatorException("LIST API method does not return a list or %s".formatted(ApiPaginatedResult.class.getSimpleName()));
            }
        }
        else if (modelMethod.returnType().resourceType() == ModelResourceType.LIST) {
            throw new ValidatorException("Only LIST API methods may return a collection type");
        }
        else if (modelMethod.returnType().resourceType() == ModelResourceType.PAGINATED_RESULT) {
            throw new ValidatorException("Only LIST API methods may return a %s".formatted(ApiPaginatedResult.class.getSimpleName()));
        }

        Collection<Class<?>> allowedParameterTypes = ALLOWED_PARAMETER_TYPES.getOrDefault(modelMethod.methodType(), ImmutableSet.of());

        for (Parameter parameter : modelMethod.method().getParameters()) {
            if (hasJaxRsAnnotation(parameter.getDeclaredAnnotations(), true)) {
                throw new ValidatorException("Method parameter %s has JAX-RS annotations".formatted(parameter.getName()));
            }

            Context context = parameter.getDeclaredAnnotation(Context.class);
            Suspended suspended = parameter.getDeclaredAnnotation(Suspended.class);
            ApiParameter apiParameter = parameter.getDeclaredAnnotation(ApiParameter.class);
            int expectedAnnotationCount = ((context != null) || (apiParameter != null) || (suspended != null)) ? 1 : 0;

            if (parameter.getDeclaredAnnotations().length != expectedAnnotationCount) {
                throw new ValidatorException("Invalid annotations on parameter %s".formatted(parameter.getName()));
            }

            if (apiParameter != null) {
                if (ApiId.class.isAssignableFrom(parameter.getType())) {
                    validationContext.validateId(parameter.getType());
                }
                else if (!ApiResponseHeaders.class.isAssignableFrom(parameter.getType())) {
                    validateOptionalParameter(modelMethod, parameter, allowedParameterTypes, apiParameter);
                }
            }
            else if ((context == null) && (suspended == null)) {
                modelMethod.requestBody().ifPresent(requestBody -> validateRequestBody(modelMethod.methodType(), requestBody, serviceTraits));
            }
        }

        validateQuotas(modelMethod, serviceTraits);
    }

    private static void validateOptionalParameter(ModelMethod method, Parameter parameter, Collection<Class<?>> allowedParameterTypes, ApiParameter apiParameter)
    {
        ModelOptionalParameter optionalParameter = OPTIONAL_PARAMETER_MAP.get(parameter.getType());

        validateApiParameter(method.method(), parameter, allowedParameterTypes, apiParameter, optionalParameter.metadata().contains(MULTIPLE_ALLOWED));

        boolean hasAllowedValues = (apiParameter.allowedValues().length > 0);

        if (optionalParameter.metadata().contains(REQUIRES_ALLOWED_VALUES) && !hasAllowedValues) {
            throw new ValidatorException("%s parameter requires allowedValues".formatted(optionalParameter.type()));
        }
    }

    private static void validateApiParameter(Method method, Parameter parameter, Collection<Class<?>> allowedParameterTypes, ApiParameter apiParameter, boolean multipleAllowed)
    {
        if (apiParameter == null) {
            throw new ValidatorException("Method is missing @%s for %s parameter".formatted(ApiParameter.class.getSimpleName(), parameter.getType().getSimpleName()));
        }

        if (!allowedParameterTypes.contains(parameter.getType())) {
            throw new ValidatorException("Method cannot have a %s parameter".formatted(parameter.getType().getSimpleName()));
        }

        if (!multipleAllowed) {
            long parametersOfThisType = Stream.of(method.getParameterTypes()).filter(type -> type.equals(parameter.getType())).count();
            if (parametersOfThisType > 1) {
                throw new ValidatorException("Method has multiple %s parameters".formatted(parameter.getType().getSimpleName()));
            }
        }
    }

    private static void validateRequestBody(ApiType apiType, ModelResource modelResource, Set<ApiServiceTrait> serviceTraits)
    {
        boolean hasVersion = modelResource.modifiers().contains(HAS_VERSION);
        boolean hasResourceId = modelResource.modifiers().contains(HAS_RESOURCE_ID);
        boolean isPatch = modelResource.modifiers().contains(PATCH);
        boolean isReadOnly = ModelResourceModifier.hasReadOnly(modelResource.modifiers());

        if (apiType == ApiType.CREATE) {
            if (hasVersion) {
                throw new ValidatorException("Method is Create. %s resource parameter has an %s".formatted(modelResource.type(), ApiResourceVersion.class.getSimpleName()));
            }
        }
        else {
            if (!hasVersion && !isPatch && serviceTraits.contains(ApiServiceTrait.USES_VERSIONED_RESOURCES)) {
                throw new ValidatorException("Method has a %s resource parameter that is missing an %s".formatted(modelResource.type(), ApiResourceVersion.class.getSimpleName()));
            }
            if (!hasResourceId && !isPatch && serviceTraits.contains(ApiServiceTrait.REQUIRES_RESOURCE_IDS)) {
                throw new ValidatorException("Method has a %s resource parameter that is missing a resource ID".formatted(modelResource.type()));
            }
        }
        if (isReadOnly) {
            throw new ValidatorException("Method has a read only %s resource".formatted(modelResource.type()));
        }
        if ((apiType != ApiType.CREATE) && (apiType != ApiType.UPDATE)) {
            throw new ValidatorException("Method is %s and cannot have a request body".formatted(apiType));
        }
    }

    private static void validateQuotas(ModelMethod modelMethod, Set<ApiServiceTrait> serviceTraits)
    {
        if (modelMethod.methodType() == ApiType.CREATE) {
            ImmutableSet.Builder<String> quotasBuilder = ImmutableSet.<String>builder()
                    .addAll(modelMethod.quotas());

            ModelResource targetResource = modelMethod.requestBody().orElseGet(modelMethod::returnType);
            quotasBuilder.addAll(targetResource.quotas());
            Set<String> quotas = quotasBuilder.build();

            if (quotas.isEmpty() && serviceTraits.contains(ApiServiceTrait.QUOTAS_REQUIRED)) {
                boolean isBeta = modelMethod.traits().contains(ApiTrait.BETA);
                String message = "Create methods must use a resource that specifies quotas or explicitly specify quotas";
                if (isBeta) {
                    log.warn(message);
                }
                else {
                    throw new ValidatorException(message);
                }
            }
        }
    }

    private static boolean hasJaxRsAnnotation(Annotation[] annotations, boolean allowContextSuspended)
    {
        return Stream.of(annotations)
                .filter(not(annotation -> allowContextSuspended && (annotation.annotationType().equals(Context.class) || annotation.annotationType().equals(Suspended.class))))
                .anyMatch(annotation -> annotation.annotationType().getPackageName().startsWith("jakarta.ws.rs"));
    }
}

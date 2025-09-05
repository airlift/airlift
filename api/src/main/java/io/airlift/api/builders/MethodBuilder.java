package io.airlift.api.builders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDelete;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiList;
import io.airlift.api.ApiModifier;
import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiResponse;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiStreamResponse;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiType;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ApiValidateOnly;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelOptionalParameter;
import io.airlift.api.model.ModelOptionalParameter.ExternalParameter;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelResourceType;
import io.airlift.api.model.ModelResponse;
import io.airlift.api.model.ModelSupportsIdLookup;
import io.airlift.api.responses.ApiBadRequest;
import io.airlift.api.responses.ApiForbidden;
import io.airlift.api.responses.ApiUnauthorized;
import io.airlift.api.validation.ValidatorException;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.DEFAULT_PAGE_SIZE;
import static io.airlift.api.ApiPagination.PAGE_SIZE_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_TOKEN_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiValidateOnly.VALIDATE_ONLY_PARAMETER_NAME;
import static io.airlift.api.internals.Generics.extractGenericParameter;
import static io.airlift.api.internals.Mappers.buildHeaderName;
import static io.airlift.api.internals.Mappers.openApiName;
import static io.airlift.api.internals.Mappers.resourceFromPossibleId;
import static io.airlift.api.model.ModelOptionalParameter.Location.HEADER;
import static io.airlift.api.model.ModelOptionalParameter.Location.QUERY;
import static io.airlift.api.model.ModelOptionalParameter.Metadata.MULTIPLE_ALLOWED;
import static io.airlift.api.model.ModelOptionalParameter.Metadata.REQUIRES_ALLOWED_VALUES;
import static io.airlift.api.model.ModelOptionalParameter.Metadata.VALIDATES_ARGUMENT;
import static io.airlift.api.model.ModelResourceModifier.IS_MULTIPART_FORM;
import static io.airlift.api.model.ModelResourceModifier.IS_STREAMING_RESPONSE;
import static io.airlift.api.model.ModelResourceModifier.VOID;
import static java.util.Objects.requireNonNull;

public class MethodBuilder
{
    public static final Collection<ExternalParameter> FILTER_EXTERNAL = ImmutableSet.of(new ExternalParameter("", "Query filter", String.class));
    public static final Collection<ExternalParameter> FILTER_LIST_EXTERNAL = ImmutableSet.of(new ExternalParameter("", "Query filter", String[].class));
    public static final Collection<ExternalParameter> HEADER_EXTERNAL = ImmutableSet.of(new ExternalParameter("", "Header", String.class));
    public static final Collection<ExternalParameter> ORDER_BY_EXTERNAL = ImmutableSet.of(new ExternalParameter(ORDER_BY_PARAMETER_NAME, "Sort/ordering in standard SQL ORDER BY format.", String.class));
    public static final Collection<ExternalParameter> PAGINATION_EXTERNAL = ImmutableSet.of(
            new ExternalParameter(PAGE_TOKEN_QUERY_PARAMETER_NAME, "Pagination token", String.class),
            new ExternalParameter(PAGE_SIZE_QUERY_PARAMETER_NAME, "Page size or 0 for default (current maximum is " + DEFAULT_PAGE_SIZE + ")", int.class));
    public static final Collection<ExternalParameter> VALIDATE_ONLY_EXTERNAL = ImmutableSet.of(new ExternalParameter(VALIDATE_ONLY_PARAMETER_NAME, "If true validate only without taking any action", boolean.class));
    public static final Collection<ExternalParameter> MODIFIER_EXTERNAL = ImmutableSet.of(new ExternalParameter("", "", boolean.class));

    public static final Map<Class<?>, ModelOptionalParameter> OPTIONAL_PARAMETER_MAP = ImmutableMap.of(
            ApiFilter.class, new ModelOptionalParameter(QUERY, ApiFilter.class, FILTER_EXTERNAL).withMetadata(MULTIPLE_ALLOWED),
            ApiFilterList.class, new ModelOptionalParameter(QUERY, ApiFilterList.class, FILTER_LIST_EXTERNAL).withMetadata(MULTIPLE_ALLOWED),
            ApiOrderBy.class, new ModelOptionalParameter(QUERY, ApiOrderBy.class, ORDER_BY_EXTERNAL).withMetadata(VALIDATES_ARGUMENT, REQUIRES_ALLOWED_VALUES),
            ApiHeader.class, new ModelOptionalParameter(HEADER, ApiHeader.class, HEADER_EXTERNAL).withMetadata(MULTIPLE_ALLOWED),
            ApiModifier.class, new ModelOptionalParameter(QUERY, ApiModifier.class, MODIFIER_EXTERNAL).withMetadata(MULTIPLE_ALLOWED),
            ApiPagination.class, new ModelOptionalParameter(QUERY, ApiPagination.class, PAGINATION_EXTERNAL).withMetadata(VALIDATES_ARGUMENT),
            ApiValidateOnly.class, new ModelOptionalParameter(QUERY, ApiValidateOnly.class, VALIDATE_ONLY_EXTERNAL));

    static {
        // validate that optional parameter map doesn't have incorrect naming
        List<Type> badNamings = OPTIONAL_PARAMETER_MAP.values()
                .stream()
                .filter(parameter -> parameter.externalParameters().stream().filter(externalParameter -> externalParameter.name().isEmpty()).count() > 1)
                .map(ModelOptionalParameter::type)
                .collect(toImmutableList());
        if (!badNamings.isEmpty()) {
            throw new RuntimeException("There are OPTIONAL_PARAMETER_MAP with more than one empty name. " + badNamings);
        }
    }

    private final Method method;
    private final Map<Method, ModelMethod> builtMethods;
    private final Function<Type, ResourceBuilder> resourceBuilder;
    private final Map<Class<?>, ModelResponse> responses;

    private MethodBuilder(Method method, Map<Method, ModelMethod> builtMethods, Function<Type, ResourceBuilder> resourceBuilder, Map<Class<?>, ModelResponse> responses)
    {
        this.method = requireNonNull(method, "method is null");
        this.builtMethods = requireNonNull(builtMethods, "builtMethods is null");
        this.resourceBuilder = requireNonNull(resourceBuilder, "resourceBuilder is null");
        this.responses = requireNonNull(responses, "responses is null");    // do not copy
    }

    public static MethodBuilder methodBuilder(Method method)
    {
        return new MethodBuilder(method, new HashMap<>(), ResourceBuilder::resourceBuilder, new HashMap<>());
    }

    public static MethodBuilder methodBuilder(Method method, Function<Type, ResourceBuilder> resourceBuilderSupplier)
    {
        return new MethodBuilder(method, new HashMap<>(), resourceBuilderSupplier, new HashMap<>());
    }

    public static MethodBuilder methodBuilder(Method method, MethodBuilder from)
    {
        return new MethodBuilder(method, from.builtMethods, from.resourceBuilder, from.responses);
    }

    public MethodBuilder toBuilder(Method method)
    {
        return new MethodBuilder(method, builtMethods, resourceBuilder, responses);
    }

    public Optional<ModelMethod> build()
    {
        return internalBuildAndAdd();
    }

    private Optional<ModelMethod> internalBuildAndAdd()
    {
        Optional<ModelMethod> modelMethod = internalBuild();
        modelMethod.ifPresent(m -> builtMethods.put(m.method(), m));
        return modelMethod;
    }

    private Optional<ModelMethod> internalBuild()
    {
        ModelMethod modelMethod = builtMethods.get(method);
        if (modelMethod != null) {
            return Optional.of(modelMethod);
        }

        ApiGet apiGet = method.getAnnotation(ApiGet.class);
        ApiList apiList = method.getAnnotation(ApiList.class);
        ApiCreate apiCreate = method.getAnnotation(ApiCreate.class);
        ApiUpdate apiUpdate = method.getAnnotation(ApiUpdate.class);
        ApiDelete apiDelete = method.getAnnotation(ApiDelete.class);
        ApiCustom apiCustom = method.getAnnotation(ApiCustom.class);
        List<? extends Annotation> apiAnnotations = Stream.of(apiGet, apiList, apiCreate, apiUpdate, apiDelete, apiCustom).filter(Objects::nonNull).collect(toImmutableList());

        AtomicReference<ModelResource> modelResource = new AtomicReference<>();
        Optional<ModelMethod> maybeModelMethod = switch (apiAnnotations.size()) {
            case 0 -> Optional.empty();
            case 1 -> {
                Annotation apiObj = apiAnnotations.getFirst();
                Metadata metadata = toMetadata(apiObj);
                Parameters parameters = buildParameters(method, modelResource, metadata.type());
                Set<ModelResponse> responses = buildResponses(metadata);
                yield Optional.of(buildMethod(metadata, method, parameters, responses));
            }
            default -> throw new ValidatorException("API method has multiple @Api* annotations");
        };

        return applyRequestBody(method, maybeModelMethod, modelResource.get()).map(this::applyQuotas);
    }

    private record Parameters(List<ModelResource> parameters, List<ModelOptionalParameter> optionalParameters) {}

    private record Metadata(ApiType type, String description, Optional<String> verb, Class<?>[] responses, ApiTrait[] traits, String[] quotas, Optional<String> openApiName) {}

    private Metadata toMetadata(Object apiObj)
    {
        if (apiObj instanceof ApiGet apiGet) {
            return new Metadata(ApiType.GET, apiGet.description(), Optional.empty(), apiGet.responses(), apiGet.traits(), new String[] {}, openApiName(apiGet.openApiAlternateName()));
        }
        if (apiObj instanceof ApiList apiList) {
            return new Metadata(ApiType.LIST, apiList.description(), Optional.empty(), apiList.responses(), apiList.traits(), new String[] {}, openApiName(apiList.openApiAlternateName()));
        }
        if (apiObj instanceof ApiCreate apiCreate) {
            return new Metadata(ApiType.CREATE, apiCreate.description(), Optional.empty(), apiCreate.responses(), apiCreate.traits(), apiCreate.quotas(), openApiName(apiCreate.openApiAlternateName()));
        }
        if (apiObj instanceof ApiUpdate apiUpdate) {
            return new Metadata(ApiType.UPDATE, apiUpdate.description(), Optional.empty(), apiUpdate.responses(), apiUpdate.traits(), new String[] {}, openApiName(apiUpdate.openApiAlternateName()));
        }
        if (apiObj instanceof ApiDelete apiDelete) {
            return new Metadata(ApiType.DELETE, apiDelete.description(), Optional.empty(), apiDelete.responses(), apiDelete.traits(), new String[] {}, openApiName(apiDelete.openApiAlternateName()));
        }
        if (apiObj instanceof ApiCustom apiCustom) {
            return new Metadata(apiCustom.type(), apiCustom.description(), Optional.of(apiCustom.verb()), apiCustom.responses(), apiCustom.traits(), apiCustom.quotas(), openApiName(apiCustom.openApiAlternateName()));
        }
        throw new RuntimeException("Internal error - unexpected API type: " + apiObj);
    }

    private ModelMethod applyQuotas(ModelMethod modelMethod)
    {
        if (modelMethod.methodType() == ApiType.CREATE) {
            ImmutableSet.Builder<String> quotasBuilder = ImmutableSet.<String>builder()
                    .addAll(modelMethod.quotas());

            ModelResource targetResource = modelMethod.requestBody().orElseGet(modelMethod::returnType);
            quotasBuilder.addAll(targetResource.quotas());
            Set<String> quotas = quotasBuilder.build();

            return modelMethod.withQuotas(quotas);
        }
        return modelMethod;
    }

    private Optional<ModelMethod> applyRequestBody(Method method, Optional<ModelMethod> modelMethod, ModelResource requestBodyResource)
    {
        if (requestBodyResource != null) {
            boolean isPatch = Arrays.stream(method.getParameterTypes()).anyMatch(ApiPatch.class::isAssignableFrom);

            return modelMethod.map(model -> {
                if (isPatch) {
                    ModelResource modifiedModelResource = requestBodyResource.withModifier(ModelResourceModifier.PATCH);
                    ModelMethod modifiedModelMethod = model.withRequestBody(modifiedModelResource);
                    return addPatchHelpToDescription(modifiedModelMethod);
                }
                return model.withRequestBody(requestBodyResource);
            });
        }

        return modelMethod;
    }

    private ModelMethod buildMethod(Metadata metadata, Method method, Parameters parameters, Set<ModelResponse> validatedResponses)
    {
        ModelResource resultResource = buildResultResource(method, metadata.type);

        Set<ApiTrait> traits = ImmutableSet.copyOf(metadata.traits());

        String description = metadata.description();
        if (traits.contains(ApiTrait.BETA)) {
            description += "\n\n**BETA API:** This API is subject to incompatible changes, or even removal, in the future.";
        }

        return new ModelMethod(
                metadata.type(),
                method,
                metadata.verb(),
                Optional.empty(),
                resultResource,
                parameters.parameters(),
                parameters.optionalParameters(),
                description,
                validatedResponses,
                traits,
                ImmutableSet.copyOf(metadata.quotas),
                metadata.openApiName);
    }

    private Set<ModelResponse> buildResponses(Metadata metadata)
    {
        Set<Class<?>> responseClasses = new HashSet<>(ImmutableSet.copyOf(metadata.responses));
        responseClasses.add(ApiBadRequest.class);
        responseClasses.add(ApiUnauthorized.class);
        responseClasses.add(ApiForbidden.class);

        return responseClasses.stream()
                .map(responseClass -> {
                    ApiResponse apiResponse = responseClass.getAnnotation(ApiResponse.class);
                    if (apiResponse == null) {
                        throw new ValidatorException("%s is missing @%s annotation".formatted(responseClass, ApiResponse.class.getSimpleName()));
                    }
                    ModelResource modelResource = resourceBuilder.apply(responseClass).build();
                    return new ModelResponse(apiResponse.status(), responseClass, modelResource);
                })
                .collect(toImmutableSet());
    }

    private Parameters buildParameters(Method method, AtomicReference<ModelResource> requestBody, ApiType apiType)
    {
        List<ModelResource> parameters = new ArrayList<>();
        List<ModelOptionalParameter> optionalParameters = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            Context context = parameter.getAnnotation(Context.class);
            Suspended suspended = parameter.getAnnotation(Suspended.class);
            ApiParameter apiParameter = parameter.getAnnotation(ApiParameter.class);
            int expectedAnnotationCount = ((context != null) || (apiParameter != null) || (suspended != null)) ? 1 : 0;

            if (parameter.getAnnotations().length != expectedAnnotationCount) {
                throw new ValidatorException("Invalid annotations on parameter %s".formatted(parameter.getName()));
            }
            if (apiParameter != null) {
                if (ApiId.class.isAssignableFrom(parameter.getType())) {
                    ModelResource modelResource = resourceBuilder.apply(resourceFromPossibleId(parameter.getType())).build();

                    if (apiParameter.allowedValues().length > 0) {
                        modelResource = modelResource.withLimitedValues(ImmutableSet.copyOf(apiParameter.allowedValues()));
                    }

                    ApiIdSupportsLookup supportsIdLookup = parameter.getType().getAnnotation(ApiIdSupportsLookup.class);
                    if (supportsIdLookup != null) {
                        modelResource = modelResource.withSupportsIdLookup(new ModelSupportsIdLookup((Class<? extends ApiId<?, ?>>) parameter.getType(), supportsIdLookup.value()));
                    }

                    parameters.add(modelResource);
                }
                else if (!ApiResponseHeaders.class.isAssignableFrom(parameter.getType())) {
                    buildOptionalParameter(parameter, apiParameter).ifPresent(optionalParameters::add);
                }
            }
            else {
                boolean isContextOrSuspended = (context != null) || (suspended != null);
                boolean isCreateOrUpdate = (apiType == ApiType.CREATE) || (apiType == ApiType.UPDATE);
                if (!isContextOrSuspended && isCreateOrUpdate) {
                    requestBody.set(buildRequestBody(requestBody, parameter));
                }
            }
        }

        return new Parameters(parameters, optionalParameters);
    }

    private ModelResource buildRequestBody(AtomicReference<ModelResource> requestBody, Parameter parameter)
    {
        if (requestBody.get() != null) {
            throw new ValidatorException("Method has more than one request body parameter");
        }

        boolean isPatch = ApiPatch.class.isAssignableFrom(parameter.getType());

        Type parameterizedType;
        if (isPatch) {
            parameterizedType = extractGenericParameter(parameter.getParameterizedType(), 0);
        }
        else {
            parameterizedType = parameter.getParameterizedType();
        }

        return resourceBuilder.apply(parameterizedType).build();
    }

    private Optional<ModelOptionalParameter> buildOptionalParameter(Parameter parameter, ApiParameter apiParameter)
    {
        ModelOptionalParameter optionalParameter = OPTIONAL_PARAMETER_MAP.get(parameter.getType());
        if (optionalParameter == null) {
            throw new ValidatorException("%s parameter is not allowed".formatted(parameter.getType().getSimpleName()));
        }

        boolean hasAllowedValues = (apiParameter.allowedValues().length > 0);

        if (optionalParameter.metadata().contains(REQUIRES_ALLOWED_VALUES) && !hasAllowedValues) {
            throw new ValidatorException("%s parameter requires allowedValues".formatted(parameter.getType().getSimpleName()));
        }

        Collection<ExternalParameter> adjustedExternalParameters = optionalParameter.externalParameters()
                .stream()
                .map(externalParameter -> {
                    if (ApiHeader.class.isAssignableFrom(parameter.getType())) {
                        externalParameter = externalParameter.withName(buildHeaderName(parameter.getName()));
                    }
                    else if (externalParameter.name().isEmpty()) {
                        externalParameter = externalParameter.withName(parameter.getName());
                    }

                    if (!apiParameter.description().isEmpty()) {
                        externalParameter = externalParameter.withDescription(apiParameter.description());
                    }

                    if (hasAllowedValues) {
                        String allowedValues = " Allowed values: " + String.join(", ", apiParameter.allowedValues());
                        externalParameter = externalParameter.withDescription(externalParameter.description() + allowedValues);
                    }
                    return externalParameter;
                })
                .collect(toImmutableSet());

        if (hasAllowedValues) {
            optionalParameter = optionalParameter.withLimitedValues(ImmutableSet.copyOf(apiParameter.allowedValues()));
        }

        return Optional.of(optionalParameter.withExternalParameters(adjustedExternalParameters));
    }

    private ModelResource buildResultResource(Method method, ApiType apiType)
    {
        boolean allowVoidResult = (apiType != ApiType.GET) && (apiType != ApiType.LIST);
        boolean allowStreamingResult = (apiType == ApiType.GET);

        Type resource = method.getGenericReturnType();

        if (resource == void.class) {
            if (allowVoidResult) {
                return new ModelResource(resource, "", "", ImmutableList.of(), ModelResourceType.RESOURCE)
                        .withModifier(VOID);
            }
            else {
                throw new ValidatorException("Method cannot have void result");
            }
        }

        boolean isPaginated = ApiPaginatedResult.class.isAssignableFrom(method.getReturnType());
        if (isPaginated) {
            resource = extractGenericParameter(resource, 0);
        }

        ModelResource validatedResource = resourceBuilder.apply(resource).build();

        if (isPaginated) {
            return validatedResource.asResourceType(ModelResourceType.PAGINATED_RESULT).withContainerType(method.getGenericReturnType());
        }

        if (validatedResource.modifiers().contains(IS_STREAMING_RESPONSE) && !allowStreamingResult) {
            throw new ValidatorException("%s is only allowed for @%s".formatted(ApiStreamResponse.class.getSimpleName(), ApiGet.class.getSimpleName()));
        }

        if (validatedResource.modifiers().contains(IS_MULTIPART_FORM)) {
            throw new ValidatorException("%s is not allowed for method returns".formatted(ApiMultiPartForm.class.getSimpleName()));
        }

        return validatedResource;
    }

    private ModelMethod addPatchHelpToDescription(ModelMethod modelMethod)
    {
        String description = modelMethod.description() + "\n\nOnly include fields you wish to update. Missing or unrecognized fields are ignored.";
        return modelMethod.withDescription(description);
    }
}

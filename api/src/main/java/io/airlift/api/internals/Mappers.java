package io.airlift.api.internals;

import com.google.common.base.CaseFormat;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiId;
import io.airlift.api.ApiModifier;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiOrderBy.Ordering;
import io.airlift.api.ApiOrderByDirection;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResponse;
import io.airlift.api.ApiValidateOnly;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.validation.ValidatorException;
import io.airlift.http.client.HttpStatus;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_SIZE_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_TOKEN_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiValidateOnly.VALIDATE_ONLY_PARAMETER_NAME;
import static io.airlift.api.internals.Generics.extractGenericParameter;
import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

public interface Mappers
{
    static HttpStatus buildStatus(Object apiResponseInstance)
    {
        requireNonNull(apiResponseInstance, "apiResponseInstance is null");

        if (!apiResponseInstance.getClass().isRecord()) {
            throw new IllegalArgumentException("Responses must be records");
        }

        ApiResponse apiResponse = apiResponseInstance.getClass().getAnnotation(ApiResponse.class);
        if (apiResponse == null) {
            throw new IllegalArgumentException("Response is missing @%s annotation".formatted(ApiResponse.class.getSimpleName()));
        }

        return apiResponse.status();
    }

    DateTimeFormatter IMF_FIX_DATE_FORMATTER = ImfFixDate.FORMATTER;

    String CUSTOM_VERB_SEPARATOR = ":";

    static String buildServicePath(ModelServiceMetadata service)
    {
        return "%s/api/v%d".formatted(service.type().id(), service.type().version());
    }

    static Optional<String> openApiName(String annotationValue)
    {
        return annotationValue.isBlank() ? Optional.empty() : Optional.of(annotationValue);
    }

    enum MethodPathMode
    {
        FOR_DISPLAY,
        FOR_BINDING,
    }

    static String buildMethodPath(ModelMethod method, MethodPathMode mode)
    {
        StringBuilder path = new StringBuilder();
        method.parameters().forEach(resource -> addResourcePath(path, resource, true, mode));

        Optional<ModelResource> lastParameter = method.parameters().isEmpty() ? Optional.empty() : Optional.of(method.parameters().getLast());
        boolean lastParameterDifferentThanReturn = lastParameter.map(p -> !p.name().equals(method.returnType().name())).orElse(true);

        if (lastParameterDifferentThanReturn) {
            // method has no IDs or resource payloads so use the return type to set the URL's resource
            boolean lastParameterDifferentThanRequestBody = method.requestBody().map(requestBody -> lastParameter.map(p -> !p.name().equals(requestBody.name())).orElse(true)).orElse(false);
            if ((method.returnType().type() == void.class) && lastParameterDifferentThanRequestBody) {
                addResourcePath(path, method.requestBody().orElseThrow(), false, mode);
            }
            else {
                addResourcePath(path, method.returnType(), false, mode);
            }
        }

        method.customVerb().ifPresent(verb -> path.append(CUSTOM_VERB_SEPARATOR).append(verb));

        return path.toString();
    }

    static String buildFullPath(ModelServiceMetadata service, ModelMethod method, MethodPathMode mode)
    {
        return "%s/%s".formatted(buildServicePath(service), buildMethodPath(method, mode));
    }

    static String buildResourceName(Type resourceType)
    {
        Class<?> resourceClass = TypeToken.of(resourceType).getRawType();
        if (ApiJsonTypes.isApiJsonType(resourceClass)) {
            return ApiJsonTypes.apiJsonResourceName(resourceClass);
        }

        ApiPolyResource apiPolyResource = resourceClass.getAnnotation(ApiPolyResource.class);
        if (apiPolyResource != null) {
            return apiPolyResource.name();
        }
        ApiResource apiResource = Optional.ofNullable(resourceClass.getAnnotation(ApiResource.class))
                .orElseThrow(() -> new ValidatorException("\"%s\" is not a valid resource type".formatted(resourceType)));
        return apiResource.name();
    }

    static Type resourceFromPossibleId(Type possibleIdType)
    {
        if (possibleIdType instanceof Class<?> clazz) {
            if (ApiId.class.isAssignableFrom(clazz)) {
                possibleIdType = ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            }
        }
        return possibleIdType;
    }

    static String buildResourceId(Type resourceType)
    {
        return buildResourceName(resourceType) + "Id";
    }

    static String buildResourceIdSpec(Type resourceType, MethodPathMode mode, Set<String> limitedValues)
    {
        return switch (limitedValues.size()) {
            case 0 -> switch (mode) {
                case FOR_BINDING -> "{" + buildResourceId(resourceType) + ": [^%s]*}".formatted(CUSTOM_VERB_SEPARATOR);
                case FOR_DISPLAY -> "{" + buildResourceId(resourceType) + "}";
            };

            case 1 -> limitedValues.iterator().next();

            default -> "{" + buildResourceId(resourceType) + ": " + limitedValues.stream().map(value -> "(" + value + ")").collect(Collectors.joining("|")) + "}";
        };
    }

    static ApiValidateOnly buildValidateOnly(UriInfo uriInfo)
    {
        return buildValidateOnly(key -> Optional.ofNullable(uriInfo.getQueryParameters().getFirst(key)));
    }

    static ApiValidateOnly buildValidateOnly(Function<String, Optional<String>> queryParameterProvider)
    {
        return queryParameterProvider.apply(VALIDATE_ONLY_PARAMETER_NAME)
                .map(value -> new ApiValidateOnly(Boolean.parseBoolean(value)))
                .orElseGet(() -> new ApiValidateOnly(false));
    }

    static ApiPagination buildPagination(UriInfo uriInfo)
    {
        return buildPagination(key -> Optional.ofNullable(uriInfo.getQueryParameters().getFirst(key)));
    }

    static ApiPagination buildPagination(Function<String, Optional<String>> queryParameterProvider)
    {
        String pageSizeParameterValue = queryParameterProvider.apply(PAGE_SIZE_QUERY_PARAMETER_NAME).orElse("0");

        int pageSize;
        try {
            pageSize = Integer.parseInt(pageSizeParameterValue);
        }
        catch (NumberFormatException e) {
            throw badRequest("Invalid page size: " + pageSizeParameterValue);
        }

        Optional<String> pageToken = queryParameterProvider.apply(PAGE_TOKEN_QUERY_PARAMETER_NAME).filter(token -> !token.isEmpty());
        return new ApiPagination(pageToken, pageSize, Optional.empty());
    }

    static String buildHeaderName(String baseName)
    {
        baseName = baseName.replace("_", "");
        return "X-" + CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, baseName)
                .toUpperCase(Locale.ROOT);
    }

    static ApiHeader buildHeader(HttpHeaders httpHeaders, String name)
    {
        String value = httpHeaders.getHeaderString(name);
        return new ApiHeader(Optional.ofNullable(value));
    }

    static ApiFilter<Object> buildFilter(UriInfo uriInfo, String name, Set<String> allowedValues)
    {
        requireNonNull(allowedValues, "allowedValues is null");
        List<String> values = uriInfo.getQueryParameters().get(name);
        if (values != null && values.size() > 1) {
            throw badRequest("Invalid filter %s: multiple values are not allowed".formatted(name));
        }
        return buildFilter(Optional.ofNullable(uriInfo.getQueryParameters().getFirst(name))
                .map(value -> validateFilterValue(name, value, allowedValues)));
    }

    static <T> ApiFilter<T> buildFilter(Optional<T> queryParameter)
    {
        return new ApiFilter<>(queryParameter);
    }

    static ApiFilterList<Object> buildFilterList(UriInfo uriInfo, String name, Set<String> allowedValues)
    {
        requireNonNull(allowedValues, "allowedValues is null");
        List<String> strings = uriInfo.getQueryParameters().get(name);
        List<Object> values = (strings != null) ? strings.stream()
                                                  .map(value -> validateFilterValue(name, value, allowedValues))
                                                  .collect(toImmutableList()) : ImmutableList.of();
        return buildFilterList(values);
    }

    static <T> ApiFilterList<T> buildFilterList(List<T> values)
    {
        return new ApiFilterList<>(values);
    }

    static ApiFilter<?> buildTypedFilter(UriInfo uriInfo, String name, Class<?> type, Map<String, Enum<?>> enumValues, Set<String> allowedValues)
    {
        requireNonNull(enumValues, "enumValues is null");
        Optional<?> value = buildFilter(uriInfo, name, allowedValues).mapAsString()
                .map(queryParameter -> parseTypedFilterValue(type, name, queryParameter, enumValues));
        return new ApiFilter<>(value);
    }

    static ApiFilterList<?> buildTypedFilterList(UriInfo uriInfo, String name, Class<?> type, Map<String, Enum<?>> enumValues, Set<String> allowedValues)
    {
        requireNonNull(enumValues, "enumValues is null");
        List<String> strings = buildFilterList(uriInfo, name, allowedValues).mapAsString();
        return new ApiFilterList<>(strings.stream()
                .map(value -> parseTypedFilterValue(type, name, value, enumValues))
                .collect(toImmutableList()));
    }

    static Class<?> typedFilterValueType(Type type)
    {
        Type valueType = extractGenericParameter(type, 0);
        if (!(valueType instanceof Class<?> valueClass)) {
            throw new ValidatorException("API filter type parameter must be a concrete class: %s".formatted(valueType));
        }
        if (!isSupportedTypedFilterValueType(valueClass)) {
            throw new ValidatorException(
                    "API filter type parameter must be Boolean, Integer, Long, Double, String, Instant, UUID, or a concrete enum: %s"
                            .formatted(valueClass.getTypeName()));
        }
        return valueClass;
    }

    static boolean isTypedFilterType(Type type, Class<?> expectedRawType)
    {
        if (!(type instanceof ParameterizedType parameterizedType) || !parameterizedType.getRawType().equals(expectedRawType)) {
            return false;
        }
        return !extractGenericParameter(type, 0).equals(Object.class);
    }

    static boolean isSupportedTypedFilterValueType(Class<?> type)
    {
        return type.equals(Boolean.class) ||
                type.equals(Integer.class) ||
                type.equals(Long.class) ||
                type.equals(Double.class) ||
                type.equals(String.class) ||
                type.equals(Instant.class) ||
                type.equals(UUID.class) ||
                type.isEnum();
    }

    private static Object parseTypedFilterValue(Class<?> type, String name, String value, Map<String, Enum<?>> enumValues)
    {
        try {
            if (type.equals(Boolean.class)) {
                if (value.equalsIgnoreCase("true")) {
                    return true;
                }
                if (value.equalsIgnoreCase("false")) {
                    return false;
                }
                throw new IllegalArgumentException();
            }
            if (type.equals(Integer.class)) {
                return Integer.valueOf(value);
            }
            if (type.equals(Long.class)) {
                return Long.valueOf(value);
            }
            if (type.equals(Double.class)) {
                double result = Double.parseDouble(value);
                if (!Double.isFinite(result)) {
                    throw new IllegalArgumentException();
                }
                return result;
            }
            if (type.equals(String.class)) {
                return value;
            }
            if (type.equals(Instant.class)) {
                return Instant.parse(value);
            }
            if (type.equals(UUID.class)) {
                return UUID.fromString(value);
            }
            if (type.isEnum()) {
                Enum<?> enumConstant = enumValues.get(value);
                if (enumConstant != null) {
                    return enumConstant;
                }
            }
        }
        catch (RuntimeException _) {
            throw badRequest("Invalid filter %s: %s".formatted(name, value));
        }
        throw badRequest("Invalid filter %s: %s".formatted(name, value));
    }

    private static String validateFilterValue(String name, String value, Set<String> allowedValues)
    {
        if (!allowedValues.isEmpty() && !allowedValues.contains(value)) {
            throw badRequest("Invalid filter %s: %s".formatted(name, value));
        }
        return value;
    }

    static ApiOrderBy buildOrderBy(UriInfo uriInfo)
    {
        return buildOrderBy(Optional.ofNullable(uriInfo.getQueryParameters().getFirst(ORDER_BY_PARAMETER_NAME)));
    }

    static ApiOrderBy buildOrderBy(Optional<String> queryParameter)
    {
        List<Ordering> orderings = queryParameter.map(spec -> Splitter.on(',').trimResults().omitEmptyStrings().splitToList(spec)
                        .stream()
                        .map(sort -> {
                            List<String> parts = Splitter.on(' ').trimResults().omitEmptyStrings().limit(2).splitToList(sort);
                            return switch (parts.size()) {
                                case 0 -> invalidOrderBy(sort);
                                case 1 -> new Ordering(parts.get(0), ApiOrderByDirection.ASCENDING);
                                default -> switch (parts.get(1).toUpperCase(Locale.ROOT)) {
                                    case "ASC" -> new Ordering(parts.getFirst(), ApiOrderByDirection.ASCENDING);
                                    case "DESC" -> new Ordering(parts.getFirst(), ApiOrderByDirection.DESCENDING);
                                    default -> invalidOrderBy(sort);
                                };
                            };
                        })
                        .collect(toImmutableList()))
                .orElseGet(ImmutableList::of);
        return new ApiOrderBy(orderings);
    }

    static ApiModifier buildModifier(UriInfo uriInfo, String name)
    {
        return buildModifier(Optional.ofNullable(uriInfo.getQueryParameters().getFirst(name)));
    }

    static ApiModifier buildModifier(Optional<String> queryParameter)
    {
        return new ApiModifier(Boolean.parseBoolean(queryParameter.orElse("false")));
    }

    private static void addResourcePath(StringBuilder path, ModelResource resource, boolean addId, MethodPathMode mode)
    {
        if (resource.type() == void.class) {
            return;
        }
        if (!path.isEmpty()) {
            path.append("/");
        }

        path.append(buildResourceName(resource.type()));

        if (addId) {
            path.append("/").append(buildResourceIdSpec(resource.type(), mode, resource.limitedValues()));
        }
    }

    private static Ordering invalidOrderBy(String sort)
    {
        throw badRequest("Invalid sort statement: " + sort);
    }
}

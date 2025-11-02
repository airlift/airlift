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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_SIZE_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_TOKEN_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiValidateOnly.VALIDATE_ONLY_PARAMETER_NAME;
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
                addResourcePath(path, method.requestBody().get(), false, mode);
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

    static ApiFilter buildFilter(UriInfo uriInfo, String name)
    {
        return buildFilter(Optional.ofNullable(uriInfo.getQueryParameters().getFirst(name)));
    }

    static ApiFilter buildFilter(Optional<Object> queryParameter)
    {
        return new ApiFilter(queryParameter);
    }

    static ApiFilterList buildFilterList(UriInfo uriInfo, String name)
    {
        List<String> strings = uriInfo.getQueryParameters().get(name);
        List<Object> values = (strings != null) ? strings.stream().collect(toImmutableList()) : ImmutableList.of();
        return buildFilterList(values);
    }

    static ApiFilterList buildFilterList(List<Object> values)
    {
        return new ApiFilterList(values);
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

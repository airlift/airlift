package io.airlift.api.binding;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdLookup;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiModifier;
import io.airlift.api.ApiMultiPart;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiValidateOnly;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.internals.Mappers.buildFilter;
import static io.airlift.api.internals.Mappers.buildFilterList;
import static io.airlift.api.internals.Mappers.buildHeader;
import static io.airlift.api.internals.Mappers.buildHeaderName;
import static io.airlift.api.internals.Mappers.buildModifier;
import static io.airlift.api.internals.Mappers.buildOrderBy;
import static io.airlift.api.internals.Mappers.buildResourceId;
import static io.airlift.api.internals.Mappers.buildValidateOnly;
import static io.airlift.api.internals.Mappers.resourceFromPossibleId;
import static io.airlift.api.responses.ApiException.badRequest;
import static io.airlift.api.responses.ApiException.internalError;
import static io.airlift.api.responses.ApiException.notFound;
import static java.util.Objects.requireNonNull;

class SpecialApiTypeValueParamProvider
        implements ValueParamProvider
{
    private final Map<Class<? extends ApiId<?, ?>>, ApiIdLookup<? extends ApiId<?, ?>>> idLookups;
    private final ObjectMapper objectMapper;

    @Inject
    SpecialApiTypeValueParamProvider(Map<Class<? extends ApiId<?, ?>>, ApiIdLookup<? extends ApiId<?, ?>>> idLookups, ObjectMapper objectMapper)
    {
        this.idLookups = ImmutableMap.copyOf(idLookups);
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @Override
    public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
    {
        if (ApiValidateOnly.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildValidateOnly(containerRequest.getUriInfo());
        }
        if (ApiFilter.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildFilter(containerRequest.getUriInfo(), getParameterName(parameter, containerRequest));
        }
        if (ApiFilterList.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildFilterList(containerRequest.getUriInfo(), getParameterName(parameter, containerRequest));
        }
        if (ApiOrderBy.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> validate(parameter, buildOrderBy(containerRequest.getUriInfo()));
        }
        if (ApiHeader.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildHeader(containerRequest, buildHeaderName(getParameterName(parameter, containerRequest)));
        }
        if (ApiModifier.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildModifier(containerRequest.getUriInfo(), getParameterName(parameter, containerRequest));
        }
        if (ApiId.class.isAssignableFrom(parameter.getRawType())) {
            String resourceId = buildResourceId(resourceFromPossibleId(parameter.getType()));
            return containerRequest -> readId(containerRequest, resourceId, parameter.getRawType(), parameter.getAnnotation(ApiParameter.class));
        }
        if (ApiMultiPart.class.isAssignableFrom(parameter.getRawType())) {
            return new MultiPartReader(parameter.getType(), objectMapper);
        }
        if (ApiResponseHeaders.class.isAssignableFrom(parameter.getRawType())) {
            return this::responseHeaders;
        }
        return null;
    }

    @Override
    public PriorityType getPriority()
    {
        return Priority.HIGH;
    }

    private ApiResponseHeaders responseHeaders(ContainerRequest containerRequest)
    {
        ApiResponseHeaders existingResponseHeaders = (ApiResponseHeaders) containerRequest.getProperty(ApiResponseHeaders.class.getName());
        if (existingResponseHeaders != null) {
            return existingResponseHeaders;
        }

        Multimap<String, String> multimap = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);
        ApiResponseHeaders responseHeaders = () -> multimap;
        containerRequest.setProperty(ApiResponseHeaders.class.getName(), responseHeaders);
        return responseHeaders;
    }

    private ApiOrderBy validate(Parameter parameter, ApiOrderBy orderBy)
    {
        ApiParameter apiParameter = requireNonNull(parameter.getAnnotation(ApiParameter.class), "ApiParameter is missing");

        Set<String> orderByValues = orderBy.orderings().stream().map(ApiOrderBy.Ordering::field).collect(toImmutableSet());
        Set<String> allowedValues = ImmutableSet.copyOf(apiParameter.allowedValues());

        Set<String> illegalValues = Sets.difference(orderByValues, allowedValues);
        if (!illegalValues.isEmpty()) {
            throw badRequest("Invalid %s: %s".formatted(ORDER_BY_PARAMETER_NAME, String.join(", ", illegalValues)));
        }

        return orderBy;
    }

    private String getParameterName(Parameter jerseyParameter, ContainerRequest containerRequest)
    {
        int index = -1;
        for (int i = 0; i < containerRequest.getUriInfo().getMatchedResourceMethod().getInvocable().getParameters().size(); ++i) {
            // must do identity comparison - only way of correlating parameter
            if (containerRequest.getUriInfo().getMatchedResourceMethod().getInvocable().getParameters().get(i) == jerseyParameter) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new RuntimeException("Could not find matching parameter");
        }
        java.lang.reflect.Parameter[] parameters = containerRequest.getUriInfo().getMatchedResourceMethod().getInvocable().getDefinitionMethod().getParameters();
        if (index >= parameters.length) {
            throw new RuntimeException("Not enough method parameters");
        }
        return parameters[index].getName();
    }

    private Object readId(ContainerRequest containerRequest, String resourceId, Class<?> rawType, ApiParameter apiParameter)
    {
        String value = getParameterValue(containerRequest, resourceId, apiParameter, true);

        if (ApiId.class.isAssignableFrom(rawType)) {
            ApiIdLookup<? extends ApiId<?, ?>> idLookup = idLookups.get(rawType);
            if (idLookup != null) {
                ApiIdSupportsLookup supportsIdLookup = rawType.getAnnotation(ApiIdSupportsLookup.class);
                if (supportsIdLookup == null) {
                    // should never get here - ApiModule binding should have validated this
                    throw internalError("Internal error at readId");
                }

                String rawValue = getParameterValue(containerRequest, resourceId, apiParameter, false);
                String lookupPrefix = supportsIdLookup.value() + ApiIdSupportsLookup.LOOKUP_SEPARATOR;

                // check must be on the raw value to allow for the lookup prefix to be used (encoded) as a regular ID value
                // also check the decoded value as an added safety. The lookup prefix should _not_ be encoded.
                if (rawValue.startsWith(lookupPrefix) && value.startsWith(lookupPrefix)) {
                    // raw value starts with annotation's prefix value - apply the builder on the decoded value sans the prefix
                    String adjustedValue = value.substring(lookupPrefix.length());
                    return idLookup.lookup(containerRequest, adjustedValue)
                            .orElseThrow(() -> notFound("%s not found: %s".formatted(rawType.getSimpleName(), value), ImmutableList.of(rawType.getSimpleName())));
                }
            }

            try {
                return rawType.getConstructor(String.class).newInstance(value);
            }
            catch (Exception e) {
                throw badRequest("BadId: %s = %s".formatted(rawType.getSimpleName(), value));
            }
        }

        return value;
    }

    private static String getParameterValue(ContainerRequest containerRequest, String resourceId, ApiParameter apiParameter, boolean decode)
    {
        return (apiParameter.allowedValues().length == 1) ? apiParameter.allowedValues()[0] : containerRequest.getUriInfo().getPathParameters(decode).getFirst(resourceId);
    }
}

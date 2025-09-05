package io.airlift.api.binding;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelServices;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Predicate;

import static io.airlift.api.binding.JaxrsUtil.findApiResourceMethod;
import static io.airlift.api.internals.Mappers.IMF_FIX_DATE_FORMATTER;
import static java.util.Objects.requireNonNull;

class JaxrsMethodFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private final ModelServices modelServices;
    private final Map<Method, ModelDeprecation> deprecations;
    private final Map<Predicate<ModelMethod>, ContainerRequestFilter> requestFilters;
    private final Map<Predicate<ModelMethod>, ContainerResponseFilter> responseFilters;

    @Inject
    JaxrsMethodFilter(ModelServices modelServices, Map<Method, ModelDeprecation> deprecations, Map<Predicate<ModelMethod>, ContainerRequestFilter> requestFilters, Map<Predicate<ModelMethod>, ContainerResponseFilter> responseFilters)
    {
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
        this.deprecations = ImmutableMap.copyOf(deprecations);
        this.requestFilters = ImmutableMap.copyOf(requestFilters);
        this.responseFilters = ImmutableMap.copyOf(responseFilters);
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        if (!requestFilters.isEmpty()) {
            JaxrsUtil.findApiServiceMethod(requestContext, modelServices).ifPresent(modelMethod -> requestFilters.entrySet().stream().filter(entry -> entry.getKey().test(modelMethod)).forEach(entry -> {
                try {
                    entry.getValue().filter(requestContext);
                }
                catch (IOException e) {
                    throw new WebApplicationException(e);
                }
            }));
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException
    {
        ApiResponseHeaders responseHeaders = (ApiResponseHeaders) requestContext.getProperty(ApiResponseHeaders.class.getName());
        if (responseHeaders != null) {
            responseHeaders.headers().asMap().forEach((name, value) -> responseContext.getHeaders().addAll(name, ImmutableList.copyOf(value)));
        }

        findApiResourceMethod(requestContext).ifPresent(resourceMethod -> {
            if (!responseFilters.isEmpty()) {
                JaxrsUtil.findApiServiceFromMethod(resourceMethod, modelServices).ifPresent(modelMethod -> responseFilters.entrySet().stream().filter(entry -> entry.getKey().test(modelMethod)).forEach(entry -> {
                    try {
                        entry.getValue().filter(requestContext, responseContext);
                    }
                    catch (IOException e) {
                        throw new WebApplicationException(e);
                    }
                }));
            }

            ModelDeprecation modelDeprecation = deprecations.get(resourceMethod.getInvocable().getDefinitionMethod());
            if (modelDeprecation != null) {
                // see https://datatracker.ietf.org/doc/html/draft-dalal-deprecation-header-03
                MultivaluedMap<String, Object> headers = responseContext.getHeaders();
                headers.add("Deprecated", modelDeprecation.deprecationDate().map(instant -> instant.atOffset(ZoneOffset.UTC)).map(IMF_FIX_DATE_FORMATTER::format).orElse("true"));
                modelDeprecation.newImplementation().ifPresent(link -> {
                    String appliedLink = UriBuilder.fromUri(((ContainerRequest) requestContext.getRequest()).getBaseUri()).path(link).toTemplate();
                    headers.add("Link", "<%s>; rel=\"successor-version\"".formatted(appliedLink));
                });
            }
        });
    }
}

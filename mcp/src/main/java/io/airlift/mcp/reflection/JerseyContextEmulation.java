package io.airlift.mcp.reflection;

import com.google.inject.Inject;
import io.airlift.mcp.handler.RequestContext;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Providers;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static jakarta.ws.rs.core.MediaType.WILDCARD_TYPE;
import static java.util.Objects.requireNonNull;

public class JerseyContextEmulation
{
    private final List<ValueParamProvider> valueParamProviders;
    private final ResourceConfig application;

    @Inject
    public JerseyContextEmulation(ResourceConfig application)
    {
        valueParamProviders = application.getSingletons()
                .stream()
                .flatMap(instance -> (instance instanceof ValueParamProvider valueParamProvider) ? Stream.of(valueParamProvider) : Stream.empty())
                .sorted(Comparator.<ValueParamProvider>comparingInt(valueParamProvider -> valueParamProvider.getPriority().getWeight()).reversed())
                .collect(toImmutableList());
        this.application = requireNonNull(application, "application is null");
    }

    public interface InternalContextResolver
    {
        Object resolve(RequestContext requestContext);
    }

    public Optional<InternalContextResolver> resolveContextInstance(Class<?> declaringClass, Class<?> rawType, Type type, Annotation[] annotations)
    {
        if (Request.class.isAssignableFrom(rawType)) {
            return Optional.of((RequestContext::request));
        }

        if (Application.class.isAssignableFrom(rawType)) {
            return Optional.of(_ -> application);
        }

        if (UriInfo.class.isAssignableFrom(rawType)) {
            return Optional.of(requestContext -> requireContainerRequest(requestContext).getUriInfo());
        }

        if (HttpHeaders.class.isAssignableFrom(rawType)) {
            return Optional.of(JerseyContextEmulation::requireContainerRequest);    // ContainerRequest implements HttpHeaders
        }

        if (SecurityContext.class.isAssignableFrom(rawType)) {
            return Optional.of(requestContext -> requireContainerRequest(requestContext).getSecurityContext());
        }

        if (Providers.class.isAssignableFrom(rawType)) {
            return Optional.of(RequestContext::providers);
        }

        Parameter parameter = Parameter.create(declaringClass, declaringClass, false, rawType, type, annotations);

        Optional<? extends Function<ContainerRequest, ?>> provider = valueParamProviders.stream()
                .flatMap(valueParamProvider -> Optional.ofNullable(valueParamProvider.getValueProvider(parameter)).stream())
                .findFirst();

        InternalContextResolver catchAllInternalContextResolver = provider.map(JerseyContextEmulation::paramProviderResolver)
                .orElseGet(() -> jaxrsContextResolver(rawType, type));
        return Optional.of(catchAllInternalContextResolver);
    }

    private static InternalContextResolver paramProviderResolver(Function<ContainerRequest, ?> proc)
    {
        return requestContext -> proc.apply(requireContainerRequest(requestContext));
    }

    private static InternalContextResolver jaxrsContextResolver(Class<?> rawType, Type type)
    {
        return requestContext -> {
            ContextResolver<Object> jaxrsResolver = requestContext.contextResolvers().resolve(type, firstNonNull(requireContainerRequest(requestContext).getMediaType(), WILDCARD_TYPE));
            if (jaxrsResolver != null) {
                return jaxrsResolver.getContext(rawType);
            }
            return null;
        };
    }

    private static ContainerRequest requireContainerRequest(RequestContext requestContext)
    {
        if (requestContext.request() instanceof ContainerRequest containerRequest) {
            return containerRequest;
        }
        throw new IllegalArgumentException("request is not a ContainerRequest: " + requestContext.request().getClass().getName());
    }
}

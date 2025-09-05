package io.airlift.api.binding;

import com.google.inject.Inject;
import io.airlift.api.model.ModelServices;
import io.airlift.log.Logger;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

import static io.airlift.api.binding.JaxrsUtil.findApiServiceWithMethod;
import static java.util.Objects.requireNonNull;

class JaxrsApiUsageLogFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private static final Logger log = Logger.get(JaxrsApiUsageLogFilter.class);

    private final ModelServices modelServices;

    @Inject
    public JaxrsApiUsageLogFilter(ModelServices modelServices)
    {
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext)
            throws IOException
    {
        findApiServiceWithMethod(containerRequestContext, modelServices).ifPresent(apiServiceWithMethod ->
                log.info("API call request: Service Name: [%s], Service Id: [%s], Service Version: [%s], Method Type: [%s], Custom Verb: [%s], Http Path: [%s]",
                        apiServiceWithMethod.service().service().name(),
                        apiServiceWithMethod.service().service().type().id(),
                        apiServiceWithMethod.service().service().type().version(),
                        apiServiceWithMethod.method().methodType(),
                        apiServiceWithMethod.method().customVerb().orElse(""),
                        containerRequestContext.getUriInfo().getRequestUri()));
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext)
            throws IOException
    {
        findApiServiceWithMethod(containerRequestContext, modelServices).ifPresent(apiServiceWithMethod ->
                log.info("API call response: Service Name: [%s], Service Id: [%s], Service Version: [%s], Method Type: [%s], Custom Verb: [%s], Http Path: [%s], Status Code: [%d], Success: [%s]",
                        apiServiceWithMethod.service().service().name(),
                        apiServiceWithMethod.service().service().type().id(),
                        apiServiceWithMethod.service().service().type().version(),
                        apiServiceWithMethod.method().methodType(),
                        apiServiceWithMethod.method().customVerb().orElse(""),
                        containerRequestContext.getUriInfo().getRequestUri(),
                        containerResponseContext.getStatus(),
                        containerResponseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL));
    }
}

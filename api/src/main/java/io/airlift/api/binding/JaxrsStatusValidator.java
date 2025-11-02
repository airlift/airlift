package io.airlift.api.binding;

import com.google.inject.Inject;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelServices;
import io.airlift.log.Logger;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;

import java.net.URI;

import static io.airlift.api.binding.JaxrsUtil.findApiServiceMethod;
import static java.util.Objects.requireNonNull;

@Priority(Priorities.HEADER_DECORATOR)
public class JaxrsStatusValidator
        implements ContainerResponseFilter
{
    private static final Logger log = Logger.get(JaxrsStatusValidator.class);
    private final ApiMode mode;
    private final ModelServices modelServices;

    @Inject
    JaxrsStatusValidator(ApiMode mode, ModelServices modelServices)
    {
        this.mode = requireNonNull(mode, "mode is null");
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    {
        if ((responseContext.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL)) {
            findApiServiceMethod(requestContext, modelServices).ifPresent(modelMethod ->
                    validateApiError(modelMethod, requestContext.getMethod(), responseContext.getStatus(), requestContext.getUriInfo().getRequestUri()));
        }
    }

    private void validateApiError(ModelMethod modelMethod, String httpMethod, int statusCode, URI uri)
    {
        boolean isUnauthenticated = (statusCode == 401);
        if (isUnauthenticated) {
            return;
        }

        if (modelMethod.responses().stream().noneMatch(modelResponse -> modelResponse.status().code() == statusCode)) {
            String message = "Request using non-specified status. Add the response to the \"responses\" attribute of the @ApiXXX annotation. URI: %s, HTTP Method: %s, Status type: %s".formatted(uri, httpMethod, statusCode);
            log.warn(message, uri, httpMethod, statusCode);
            if (mode == ApiMode.DEBUG) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}

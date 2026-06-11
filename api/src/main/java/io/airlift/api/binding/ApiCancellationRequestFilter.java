package io.airlift.api.binding;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.util.concurrent.CompletableFuture;

import static io.airlift.api.binding.ApiCancellationValueParamProvider.REQUEST_CANCELLATION_ATTRIBUTE;

class ApiCancellationRequestFilter
        implements ContainerRequestFilter
{
    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        // Jersey servlet requests expose servlet attributes as request context properties.
        Object value = requestContext.getProperty(REQUEST_CANCELLATION_ATTRIBUTE);
        if (value == null) {
            return;
        }
        if (!(value instanceof CompletableFuture<?>)) {
            throw new IllegalStateException("Expected CompletableFuture in request attribute: " + REQUEST_CANCELLATION_ATTRIBUTE);
        }
    }
}

package io.airlift.api.binding;

import io.airlift.api.ApiCancellation;
import org.glassfish.jersey.model.Parameter.Source;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Injects @Context ApiCancellation from the request cancellation attribute.
 */
class ApiCancellationValueParamProvider
        implements ValueParamProvider
{
    static final String REQUEST_CANCELLATION_ATTRIBUTE = "io.airlift.request-cancellation";

    @Override
    public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
    {
        if ((parameter.getSource() == Source.CONTEXT) && ApiCancellation.class.equals(parameter.getRawType())) {
            return ApiCancellationValueParamProvider::getOrCreate;
        }
        return null;
    }

    @Override
    public PriorityType getPriority()
    {
        return Priority.HIGH;
    }

    private static ApiCancellation getOrCreate(ContainerRequest request)
    {
        Object value = request.getProperty(REQUEST_CANCELLATION_ATTRIBUTE);
        if (value == null) {
            return RequestApiCancellation.empty();
        }
        if (!(value instanceof CompletableFuture<?> cancellation)) {
            throw new IllegalStateException("Expected CompletableFuture in request attribute: " + REQUEST_CANCELLATION_ATTRIBUTE);
        }
        return new RequestApiCancellation(cancellation);
    }
}

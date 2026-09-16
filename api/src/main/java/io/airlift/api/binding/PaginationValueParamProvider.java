package io.airlift.api.binding;

import io.airlift.api.ApiPagination;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

import java.util.function.Function;

import static io.airlift.api.internals.Mappers.buildPagination;

class PaginationValueParamProvider
        implements ValueParamProvider
{
    @Override
    public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
    {
        if (ApiPagination.class.isAssignableFrom(parameter.getRawType())) {
            return containerRequest -> buildPagination(containerRequest.getUriInfo());
        }
        return null;
    }

    @Override
    public PriorityType getPriority()
    {
        return Priority.HIGH;
    }
}

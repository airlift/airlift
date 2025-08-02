package io.airlift.mcp;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

import java.net.URI;
import java.util.function.Function;

public class TestingValueParam
        implements ValueParamProvider
{
    @Override
    public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
    {
        if (parameter.getRawType().equals(URI.class)) {
            return containerRequest -> containerRequest.getUriInfo().getRequestUri();
        }
        return null;
    }

    @Override
    public PriorityType getPriority()
    {
        return Priority.HIGH;
    }
}

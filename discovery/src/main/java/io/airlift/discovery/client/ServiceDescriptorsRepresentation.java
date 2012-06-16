package io.airlift.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

public class ServiceDescriptorsRepresentation
{
    private final String environment;
    private final List<ServiceDescriptor> serviceDescriptors;

    @JsonCreator
    public ServiceDescriptorsRepresentation(
            @JsonProperty("environment") String environment,
            @JsonProperty("services") List<ServiceDescriptor> serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptors);
        this.environment = environment;
        this.serviceDescriptors = ImmutableList.copyOf(serviceDescriptors);
    }

    @JsonProperty
    public String getEnvironment()
    {
        return environment;
    }

    @JsonProperty("services")
    public List<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServiceDescriptorsRepresentation");
        sb.append("{environment='").append(environment).append('\'');
        sb.append(", serviceDescriptorList=").append(serviceDescriptors);
        sb.append('}');
        return sb.toString();
    }
}

package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.proofpoint.units.Duration;

import java.util.List;

import static com.proofpoint.experimental.discovery.client.ServiceTypeFactory.serviceType;
import static java.lang.String.format;

public class ServiceDescriptors
{
    private final ServiceType type;
    private final String eTag;
    private final Duration maxAge;
    private final List<ServiceDescriptor> serviceDescriptors;

    public ServiceDescriptors(ServiceDescriptors serviceDescriptors,
            Duration maxAge,
            String eTag)
    {
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        this.type = serviceDescriptors.type;
        this.maxAge = maxAge;
        this.eTag = eTag;
        this.serviceDescriptors = serviceDescriptors.serviceDescriptors;
    }

    public ServiceDescriptors(ServiceType type,
            List<ServiceDescriptor> serviceDescriptors,
            Duration maxAge,
            String eTag)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");
        Preconditions.checkNotNull(maxAge, "maxAge is null");

        this.type = type;
        this.serviceDescriptors = serviceDescriptors;
        this.maxAge = maxAge;
        this.eTag = eTag;

        // verify service descriptors match expected type
        for (ServiceDescriptor serviceDescriptor : this.serviceDescriptors) {
            ServiceType serviceType = serviceType(serviceDescriptor.getType(), serviceDescriptor.getPool());
            if (!type.equals(serviceType)) {
                throw new DiscoveryException(format("Expected service descriptor to be %s, but was %s", type, serviceType));
            }
        }
    }

    public ServiceType getType()
    {
        return type;
    }


    public String getETag()
    {
        return eTag;
    }

    public Duration getMaxAge()
    {
        return maxAge;
    }

    public List<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors;
    }

}

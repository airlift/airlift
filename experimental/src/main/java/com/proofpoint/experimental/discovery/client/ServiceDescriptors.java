package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.proofpoint.units.Duration;

import java.util.List;

import static java.lang.String.format;

public class ServiceDescriptors
{
    private final String type;
    private final String pool;
    private final String eTag;
    private final Duration maxAge;
    private final List<ServiceDescriptor> serviceDescriptors;

    public ServiceDescriptors(ServiceDescriptors serviceDescriptors,
            Duration maxAge,
            String eTag)
    {
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        this.type = serviceDescriptors.type;
        this.pool = serviceDescriptors.pool;
        this.maxAge = maxAge;
        this.eTag = eTag;
        this.serviceDescriptors = serviceDescriptors.serviceDescriptors;
    }

    public ServiceDescriptors(String type,
            String pool,
            List<ServiceDescriptor> serviceDescriptors,
            Duration maxAge,
            String eTag)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");
        Preconditions.checkNotNull(maxAge, "maxAge is null");

        this.type = type;
        this.pool = pool;
        this.serviceDescriptors = serviceDescriptors;
        this.maxAge = maxAge;
        this.eTag = eTag;

        // verify service descriptors match expected type
        for (ServiceDescriptor serviceDescriptor : this.serviceDescriptors) {
            if (!type.equals(serviceDescriptor.getType()) || !pool.equals(serviceDescriptor.getPool())) {
                throw new DiscoveryException(format("Expected %s service descriptor from pool %s, but was %s service descriptor from pool %s",
                        type,
                        pool,
                        serviceDescriptor.getType(),
                        serviceDescriptor.getPool()));
            }
        }
    }

    public String getType()
    {
        return type;
    }

    public String getPool()
    {
        return pool;
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

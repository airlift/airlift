package com.proofpoint.experimental.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.proofpoint.experimental.discovery.client.ServiceDescriptor;
import com.proofpoint.experimental.discovery.client.ServiceSelector;

import java.util.List;

public class StaticServiceSelector implements ServiceSelector
{
    private final String type;
    private final String pool;
    private final List<ServiceDescriptor> serviceDescriptors;

    public StaticServiceSelector(ServiceDescriptor serviceDescriptor, ServiceDescriptor... serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptor, "serviceDescriptor is null");
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        this.type = serviceDescriptor.getType();
        this.pool = serviceDescriptor.getPool();
        for (ServiceDescriptor descriptor : serviceDescriptors) {
            Preconditions.checkArgument(descriptor.getType().equals(type));
            Preconditions.checkArgument(descriptor.getPool().equals(pool));
        }
        this.serviceDescriptors = ImmutableList.<ServiceDescriptor>builder().add(serviceDescriptor).add(serviceDescriptors).build();
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String getPool()
    {
        return pool;
    }

    @Override
    public List<ServiceDescriptor> selectAllServices()
    {
        return serviceDescriptors;
    }
}

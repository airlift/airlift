package io.airlift.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceSelector;

import java.util.List;

import static io.airlift.discovery.client.ServiceSelectorConfig.DEFAULT_POOL;

public class StaticServiceSelector implements ServiceSelector
{
    private final String type;
    private final String pool;
    private final List<ServiceDescriptor> serviceDescriptors;

    public StaticServiceSelector(ServiceDescriptor... serviceDescriptors)
    {
        this(ImmutableList.copyOf(serviceDescriptors));
    }

    public StaticServiceSelector(Iterable<ServiceDescriptor> serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        ServiceDescriptor serviceDescriptor = Iterables.getFirst(serviceDescriptors, null);
        if (serviceDescriptor != null) {
            this.type = serviceDescriptor.getType();
            this.pool = serviceDescriptor.getPool();
        }
        else {
            this.type = "unknown";
            this.pool = DEFAULT_POOL;
        }

        for (ServiceDescriptor descriptor : serviceDescriptors) {
            Preconditions.checkArgument(descriptor.getType().equals(type));
            Preconditions.checkArgument(descriptor.getPool().equals(pool));
        }
        this.serviceDescriptors = ImmutableList.copyOf(serviceDescriptors);
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

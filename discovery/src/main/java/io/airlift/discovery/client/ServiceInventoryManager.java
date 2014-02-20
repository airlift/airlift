package io.airlift.discovery.client;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import javax.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO: change API implementation to dedup with a Set after ServiceDescriptor fixes equals/hashCode (null UUIDs)
public class ServiceInventoryManager
        implements ServiceInventory
{
    private final Set<ServiceInventory> serviceInventories;

    @Inject
    public ServiceInventoryManager(Set<ServiceInventory> serviceInventories)
    {
        this.serviceInventories = ImmutableSet.copyOf(checkNotNull(serviceInventories, "serviceInventories is null"));
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors()
    {
        return shuffle(FluentIterable.from(serviceInventories)
                .transformAndConcat(new Function<ServiceInventory, Iterable<ServiceDescriptor>>()
                {
                    @Override
                    public Iterable<ServiceDescriptor> apply(ServiceInventory serviceInventory)
                    {
                        return serviceInventory.getServiceDescriptors();
                    }
                }));
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type)
    {
        return shuffle(FluentIterable.from(serviceInventories)
                .transformAndConcat(new Function<ServiceInventory, Iterable<ServiceDescriptor>>()
                {
                    @Override
                    public Iterable<ServiceDescriptor> apply(ServiceInventory serviceInventory)
                    {
                        return serviceInventory.getServiceDescriptors(type);
                    }
                }));
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type, final String pool)
    {
        return shuffle(FluentIterable.from(serviceInventories)
                .transformAndConcat(new Function<ServiceInventory, Iterable<ServiceDescriptor>>()
                {
                    @Override
                    public Iterable<ServiceDescriptor> apply(ServiceInventory serviceInventory)
                    {
                        return serviceInventory.getServiceDescriptors(type, pool);
                    }
                }));
    }

    private static Iterable<ServiceDescriptor> shuffle(Iterable<ServiceDescriptor> descriptors)
    {
        List<ServiceDescriptor> serviceDescriptors = Lists.newArrayList(descriptors);
        Collections.shuffle(serviceDescriptors);
        return serviceDescriptors;
    }
}

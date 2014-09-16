package io.airlift.discovery.client;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class TestingServiceInventory
        implements ServiceInventory
{
    private final ListMultimap<String, ServiceDescriptor> descriptorsByType = ArrayListMultimap.create();
    private final ListMultimap<TypeAndPool, ServiceDescriptor> descriptorsByTypeAndPool = ArrayListMultimap.create();

    public TestingServiceInventory addServiceDescriptor(ServiceDescriptor descriptor)
    {
        descriptorsByType.put(descriptor.getType(), descriptor);
        descriptorsByTypeAndPool.put(new TypeAndPool(descriptor.getType(), descriptor.getPool()), descriptor);
        return this;
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors()
    {
        return ImmutableList.copyOf(descriptorsByType.values());
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors(String type)
    {
        return ImmutableList.copyOf(descriptorsByType.get(type));
    }

    @Override
    public Iterable<ServiceDescriptor> getServiceDescriptors(String type, String pool)
    {
        return ImmutableList.copyOf(descriptorsByTypeAndPool.get(new TypeAndPool(type, pool)));
    }

    private static class TypeAndPool
    {
        private final String type;
        private final String pool;

        private TypeAndPool(String type, String pool)
        {
            this.type = checkNotNull(type, "type is null");
            this.pool = checkNotNull(pool, "pool is null");
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(type, pool);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final TypeAndPool other = (TypeAndPool) obj;
            return Objects.equals(this.type, other.type) && Objects.equals(this.pool, other.pool);
        }
    }
}

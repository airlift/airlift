package com.proofpoint.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryClient;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.discovery.client.ServiceAnnouncement;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptors;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryDiscoveryClient implements DiscoveryClient
{
    private final AtomicReference<Set<ServiceDescriptor>> announcements = new AtomicReference<Set<ServiceDescriptor>>(ImmutableSet.<ServiceDescriptor>of());
    private final ConcurrentMap<UUID, ServiceDescriptor> discovered = new MapMaker().makeMap();

    private final NodeInfo nodeInfo;
    private final Duration maxAge;

    @Inject
    public InMemoryDiscoveryClient(NodeInfo nodeInfo)
    {
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        this.nodeInfo = nodeInfo;
        maxAge = DEFAULT_DELAY;
    }

    public InMemoryDiscoveryClient(NodeInfo nodeInfo, Duration maxAge)
    {
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(maxAge, "maxAge is null");
        this.nodeInfo = nodeInfo;
        this.maxAge = maxAge;
    }

    public ServiceDescriptor addDiscoveredService(ServiceDescriptor serviceDescriptor)
    {
        Preconditions.checkNotNull(serviceDescriptor, "serviceDescriptor is null");

        return discovered.put(serviceDescriptor.getId(), serviceDescriptor);
    }

    public ServiceDescriptor remove(UUID uuid)
    {
        Preconditions.checkNotNull(uuid, "uuid is null");

        return discovered.remove(uuid);
    }

    @Override
    public CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services)
    {
        Preconditions.checkNotNull(services, "services is null");

        ImmutableSet.Builder<ServiceDescriptor> builder = ImmutableSet.builder();
        for (ServiceAnnouncement service : services) {
            builder.add(new ServiceDescriptor(service.getId(), nodeInfo.getNodeId(), service.getType(), nodeInfo.getPool(), null, service.getProperties()));
        }
        announcements.set(builder.build());
        return Futures.immediateCheckedFuture(maxAge);
    }

    @Override
    public CheckedFuture<Void, DiscoveryException> unannounce()
    {
        announcements.set(ImmutableSet.<ServiceDescriptor>of());
        return Futures.immediateCheckedFuture(null);
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type, String pool)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");

        ImmutableList.Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (ServiceDescriptor serviceDescriptor : this.announcements.get()) {
            if (serviceDescriptor.getType().equals(type) && serviceDescriptor.getPool().equals(pool)) {
                builder.add(serviceDescriptor);
            }
        }
        for (ServiceDescriptor serviceDescriptor : this.discovered.values()) {
            if (serviceDescriptor.getType().equals(type) && serviceDescriptor.getPool().equals(pool)) {
                builder.add(serviceDescriptor);
            }
        }
        return Futures.immediateCheckedFuture(new ServiceDescriptors(type, pool, builder.build(), maxAge, UUID.randomUUID().toString()));
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> refreshServices(ServiceDescriptors serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        return getServices(serviceDescriptors.getType(), serviceDescriptors.getPool());
    }
}

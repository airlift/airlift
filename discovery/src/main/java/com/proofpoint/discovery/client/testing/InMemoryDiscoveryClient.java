/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.discovery.client.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceAnnouncement;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptors;
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class InMemoryDiscoveryClient implements DiscoveryAnnouncementClient, DiscoveryLookupClient
{
    private final AtomicReference<Set<ServiceDescriptor>> announcements = new AtomicReference<Set<ServiceDescriptor>>(ImmutableSet.<ServiceDescriptor>of());
    private final ConcurrentMap<UUID, ServiceDescriptor> discovered = new MapMaker().makeMap();

    private final NodeInfo nodeInfo;
    private final Duration maxAge;

    @Inject
    public InMemoryDiscoveryClient(NodeInfo nodeInfo)
    {
        checkNotNull(nodeInfo, "nodeInfo is null");
        this.nodeInfo = nodeInfo;
        maxAge = DEFAULT_DELAY;
    }

    public InMemoryDiscoveryClient(NodeInfo nodeInfo, Duration maxAge)
    {
        checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(maxAge, "maxAge is null");
        this.nodeInfo = nodeInfo;
        this.maxAge = maxAge;
    }

    public ServiceDescriptor addDiscoveredService(ServiceDescriptor serviceDescriptor)
    {
        checkNotNull(serviceDescriptor, "serviceDescriptor is null");

        return discovered.put(serviceDescriptor.getId(), serviceDescriptor);
    }

    public ServiceDescriptor addDiscoveredService(String type, String pool, String uri)
    {
        checkNotNull(type, "type is null");
        checkNotNull(pool, "pool is null");
        checkNotNull(uri, "uri is null");

        String nodeId = UUID.randomUUID().toString();
        Map<String, String> properties = new HashMap<>();
        URI serviceUri = URI.create(uri);
        String scheme = serviceUri.getScheme();
        checkArgument(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"),
                "Unsupported scheme in uri %s", uri);
        properties.put(scheme, serviceUri.toString());

        return addDiscoveredService(new ServiceDescriptor(
                UUID.randomUUID(),
                nodeId,
                type,
                pool,
                "/somewhere/" + nodeId,
                ServiceState.RUNNING,
                properties
        ));
    }

    public ServiceDescriptor remove(UUID uuid)
    {
        checkNotNull(uuid, "uuid is null");

        return discovered.remove(uuid);
    }

    @Override
    public CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services)
    {
        checkNotNull(services, "services is null");

        ImmutableSet.Builder<ServiceDescriptor> builder = ImmutableSet.builder();
        for (ServiceAnnouncement service : services) {
            builder.add(new ServiceDescriptor(service.getId(), nodeInfo.getNodeId(), service.getType(), nodeInfo.getPool(), null, ServiceState.RUNNING, service.getProperties()));
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
    public CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type)
    {
        checkNotNull(type, "type is null");

        ImmutableList.Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (ServiceDescriptor serviceDescriptor : this.announcements.get()) {
            if (serviceDescriptor.getType().equals(type)) {
                builder.add(serviceDescriptor);
            }
        }
        for (ServiceDescriptor serviceDescriptor : this.discovered.values()) {
            if (serviceDescriptor.getType().equals(type)) {
                builder.add(serviceDescriptor);
            }
        }
        return Futures.immediateCheckedFuture(new ServiceDescriptors(type, null, builder.build(), maxAge, UUID.randomUUID().toString()));
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type, String pool)
    {
        checkNotNull(type, "type is null");
        checkNotNull(pool, "pool is null");

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
        checkNotNull(serviceDescriptors, "serviceDescriptors is null");

        return getServices(serviceDescriptors.getType(), serviceDescriptors.getPool());
    }
}

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
package io.airlift.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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

    @Override
    public ListenableFuture<List<ServiceDescriptor>> refresh()
    {
        return Futures.immediateFuture(serviceDescriptors);
    }
}

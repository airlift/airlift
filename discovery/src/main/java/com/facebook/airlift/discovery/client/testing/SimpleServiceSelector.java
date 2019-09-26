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
package com.facebook.airlift.discovery.client.testing;

import com.facebook.airlift.discovery.client.DiscoveryException;
import com.facebook.airlift.discovery.client.DiscoveryLookupClient;
import com.facebook.airlift.discovery.client.ServiceDescriptor;
import com.facebook.airlift.discovery.client.ServiceDescriptors;
import com.facebook.airlift.discovery.client.ServiceSelector;
import com.facebook.airlift.discovery.client.ServiceSelectorConfig;
import com.facebook.airlift.log.Logger;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import static com.facebook.airlift.concurrent.MoreFutures.getFutureValue;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;

public class SimpleServiceSelector
        implements ServiceSelector
{
    private static final Logger log = Logger.get(SimpleServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryLookupClient lookupClient;

    public SimpleServiceSelector(String type, ServiceSelectorConfig selectorConfig, DiscoveryLookupClient lookupClient)
    {
        requireNonNull(type, "type is null");
        requireNonNull(selectorConfig, "selectorConfig is null");
        requireNonNull(lookupClient, "client is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.lookupClient = lookupClient;
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
        try {
            ListenableFuture<ServiceDescriptors> future = lookupClient.getServices(type, pool);
            ServiceDescriptors serviceDescriptors = getFutureValue(future, DiscoveryException.class);
            return serviceDescriptors.getServiceDescriptors();
        }
        catch (DiscoveryException e) {
            log.error(e);
            return ImmutableList.of();
        }
    }

    @Override
    public ListenableFuture<List<ServiceDescriptor>> refresh()
    {
        return FluentFuture.from(lookupClient.getServices(type, pool))
                .transform(ServiceDescriptors::getServiceDescriptors, directExecutor());
    }
}

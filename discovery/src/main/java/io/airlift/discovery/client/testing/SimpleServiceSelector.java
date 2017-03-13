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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.discovery.client.DiscoveryException;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceDescriptors;
import io.airlift.discovery.client.ServiceSelector;
import io.airlift.discovery.client.ServiceSelectorConfig;
import io.airlift.log.Logger;

import java.util.List;

import static io.airlift.concurrent.MoreFutures.getFutureValue;
import static java.util.Objects.requireNonNull;

public class SimpleServiceSelector implements ServiceSelector
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
        return Futures.transform(lookupClient.getServices(type, pool), ServiceDescriptors::getServiceDescriptors);
    }
}

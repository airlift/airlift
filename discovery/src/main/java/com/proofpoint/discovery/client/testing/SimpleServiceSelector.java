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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptors;
import com.proofpoint.discovery.client.ServiceSelector;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

public class SimpleServiceSelector implements ServiceSelector
{
    private static final Logger log = Logger.get(SimpleServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryLookupClient lookupClient;

    public SimpleServiceSelector(String type, ServiceSelectorConfig selectorConfig, NodeInfo nodeInfo, DiscoveryLookupClient lookupClient)
    {
        checkNotNull(type, "type is null");
        checkNotNull(selectorConfig, "selectorConfig is null");
        checkNotNull(lookupClient, "client is null");

        this.type = type;
        this.pool = firstNonNull(selectorConfig.getPool(), nodeInfo.getPool());
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
            ServiceDescriptors serviceDescriptors = getFutureResult(future, DiscoveryException.class);
            return serviceDescriptors.getServiceDescriptors();
        }
        catch (DiscoveryException e) {
            log.error(e);
            return ImmutableList.of();
        }
    }

    // TODO: move this to a utility package
    private static <T, X extends Throwable> T getFutureResult(Future<T> future, Class<X> type)
            throws X
    {
        try {
            return future.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
        catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), type);
            throw Throwables.propagate(e.getCause());
        }
    }
}

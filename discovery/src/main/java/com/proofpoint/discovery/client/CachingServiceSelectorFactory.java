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
package com.proofpoint.discovery.client;

import com.google.inject.Inject;
import com.proofpoint.node.NodeInfo;

import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;

public class CachingServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryLookupClient lookupClient;
    private final ScheduledExecutorService executor;
    private final NodeInfo nodeInfo;

    @Inject
    public CachingServiceSelectorFactory(DiscoveryLookupClient lookupClient, @ForDiscoveryClient ScheduledExecutorService executor, NodeInfo nodeInfo)
    {
        checkNotNull(lookupClient, "client is null");
        checkNotNull(executor, "executor is null");
        checkNotNull(nodeInfo, "nodeInfo is null");

        this.lookupClient = lookupClient;
        this.executor = executor;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        checkNotNull(type, "type is null");
        checkNotNull(selectorConfig, "selectorConfig is null");

        CachingServiceSelector serviceSelector = new CachingServiceSelector(type, selectorConfig, nodeInfo);
        ServiceDescriptorsUpdater updater = new ServiceDescriptorsUpdater(serviceSelector, type, selectorConfig, nodeInfo, lookupClient, executor);
        updater.start();

        return serviceSelector;
    }
}

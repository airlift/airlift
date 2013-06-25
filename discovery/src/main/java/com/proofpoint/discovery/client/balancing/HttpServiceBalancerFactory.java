/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balancing;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ForDiscoveryClient;
import com.proofpoint.discovery.client.ServiceDescriptorsUpdater;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;

import java.util.concurrent.ScheduledExecutorService;

import static java.lang.String.format;

public final class HttpServiceBalancerFactory
{
    private final DiscoveryLookupClient lookupClient;
    private final ScheduledExecutorService executor;

    @Inject
    public HttpServiceBalancerFactory(DiscoveryLookupClient lookupClient, @ForDiscoveryClient ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(lookupClient, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");
        this.lookupClient = lookupClient;
        this.executor = executor;
    }

    public HttpServiceBalancer createHttpServiceBalancer(String type, ServiceSelectorConfig selectorConfig)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");

        HttpServiceBalancerImpl balancer = new HttpServiceBalancerImpl(format("type=[%s], pool=[%s]", type, selectorConfig.getPool()));
        ServiceDescriptorsUpdater updater = new ServiceDescriptorsUpdater(new HttpServiceBalancerListenerAdapter(balancer), type, selectorConfig, lookupClient, executor);
        updater.start();

        return balancer;
    }
}

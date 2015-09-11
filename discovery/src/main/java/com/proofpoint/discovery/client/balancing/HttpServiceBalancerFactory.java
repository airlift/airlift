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

import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ForDiscoveryClient;
import com.proofpoint.discovery.client.ServiceDescriptorsUpdater;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;
import org.weakref.jmx.ObjectNameBuilder;

import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Objects.firstNonNull;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public final class HttpServiceBalancerFactory
{
    private final DiscoveryLookupClient lookupClient;
    private final ScheduledExecutorService executor;
    private final ReportExporter reportExporter;
    private final ReportCollectionFactory reportCollectionFactory;

    @Inject
    public HttpServiceBalancerFactory(
            DiscoveryLookupClient lookupClient,
            @ForDiscoveryClient ScheduledExecutorService executor,
            ReportExporter reportExporter,
            ReportCollectionFactory reportCollectionFactory)
    {
        this.lookupClient = requireNonNull(lookupClient, "lookupClient is null");
        this.executor = requireNonNull(executor, "executor is null");
        this.reportExporter = requireNonNull(reportExporter, "reportExporter is null");
        this.reportCollectionFactory = requireNonNull(reportCollectionFactory, "reportCollectionFactory is null");
    }

    public HttpServiceBalancer createHttpServiceBalancer(String type, ServiceSelectorConfig selectorConfig, NodeInfo nodeInfo)
    {
        requireNonNull(type, "type is null");
        requireNonNull(selectorConfig, "selectorConfig is null");

        String pool = firstNonNull(selectorConfig.getPool(), nodeInfo.getPool());
        String name = new ObjectNameBuilder(HttpServiceBalancerStats.class.getPackage().getName())
                .withProperty("type", "ServiceClient")
                .withProperty("serviceType", type)
                .build();
        HttpServiceBalancerStats httpServiceBalancerStats = reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, name);
        HttpServiceBalancerImpl balancer = new HttpServiceBalancerImpl(format("type=[%s], pool=[%s]", type, pool), httpServiceBalancerStats);
        reportExporter.export(name, balancer);
        ServiceDescriptorsUpdater updater = new ServiceDescriptorsUpdater(new HttpServiceBalancerListenerAdapter(balancer), type, selectorConfig, nodeInfo, lookupClient, executor);
        updater.start();

        return balancer;
    }
}

/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;
import org.weakref.jmx.ObjectNameBuilder;

import java.net.URI;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class StaticHttpServiceBalancerProvider implements Provider<HttpServiceBalancer>
{
    private final String type;
    private final Set<URI> baseUris;
    private ReportExporter reportExporter;
    private ReportCollectionFactory reportCollectionFactory;

    StaticHttpServiceBalancerProvider(String type, Set<URI> baseUris)
    {
        this.type = requireNonNull(type, "type is null");
        this.baseUris = ImmutableSet.copyOf(baseUris);
    }

    @Inject
    public void setReportExporter(ReportExporter reportExporter)
    {
        requireNonNull(reportExporter, "reportExporter is null");
        this.reportExporter = reportExporter;
    }

    @Inject
    public void setReportCollectionFactory(ReportCollectionFactory reportCollectionFactory)
    {
        requireNonNull(reportCollectionFactory, "reportCollectionFactory is null");
        this.reportCollectionFactory = reportCollectionFactory;
    }

    @Override
    public HttpServiceBalancer get()
    {
        requireNonNull(type, "type is null");
        requireNonNull(baseUris, "baseUris is null");

        String name = new ObjectNameBuilder(HttpServiceBalancerStats.class.getPackage().getName())
                .withProperty("type", "ServiceClient")
                .withProperty("serviceType", type)
                .build();
        HttpServiceBalancerStats httpServiceBalancerStats = reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, name);
        HttpServiceBalancerImpl balancer = new HttpServiceBalancerImpl(format("type=[%s], static", type), httpServiceBalancerStats);
        reportExporter.export(name, balancer);
        balancer.updateHttpUris(baseUris);
        return balancer;
    }
}

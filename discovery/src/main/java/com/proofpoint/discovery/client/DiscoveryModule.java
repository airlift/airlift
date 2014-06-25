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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.announce.Announcement;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.HttpDiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerFactory;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.reporting.ReportCollectionFactory;
import org.weakref.jmx.ObjectNameBuilder;

import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.json.JsonCodecBinder.jsonCodecBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class DiscoveryModule
        implements Module
{
    private HttpServiceBalancerImpl discoveryBalancer = null;

    @Override
    public void configure(Binder binder)
    {
        // bind service inventory
        binder.bind(ServiceInventory.class).asEagerSingleton();
        bindConfig(binder).to(ServiceInventoryConfig.class);

        // for legacy configurations
        bindConfig(binder).to(DiscoveryClientConfig.class);

        // bind discovery client and dependencies
        binder.bind(DiscoveryLookupClient.class).to(HttpDiscoveryLookupClient.class).in(Scopes.SINGLETON);
        binder.bind(DiscoveryAnnouncementClient.class).to(HttpDiscoveryAnnouncementClient.class).in(Scopes.SINGLETON);
        jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

        // bind the http client
        discoveryBinder(binder).bindDiscoveredHttpClientWithBalancer("discovery", serviceType("discovery"), ForDiscoveryClient.class);

        // bind announcer
        binder.bind(Announcer.class).in(Scopes.SINGLETON);

        // Must create a multibinder for service announcements or construction will fail if no
        // service announcements are bound, which is legal for processes that don't have public services
        Multibinder.newSetBinder(binder, ServiceAnnouncement.class);

        binder.bind(ServiceSelectorFactory.class).to(CachingServiceSelectorFactory.class).in(Scopes.SINGLETON);
        binder.bind(HttpServiceBalancerFactory.class).in(Scopes.SINGLETON);

        binder.bind(ScheduledExecutorService.class)
                .annotatedWith(ForDiscoveryClient.class)
                .toProvider(DiscoveryExecutorProvider.class)
                .in(Scopes.SINGLETON);

        newExporter(binder).export(ServiceInventory.class).withGeneratedName();
    }


    @Provides
    @ServiceType("discovery")
    public HttpServiceBalancer createHttpServiceBalancer(ReportCollectionFactory reportCollectionFactory)
    {
        return getHttpServiceBalancerImpl(reportCollectionFactory);
    }

    @Provides
    @ServiceType("discovery")
    synchronized public HttpServiceBalancerImpl getHttpServiceBalancerImpl(ReportCollectionFactory reportCollectionFactory)
    {
        if (discoveryBalancer == null) {
            String name = new ObjectNameBuilder(HttpServiceBalancerStats.class.getPackage().getName())
                    .withProperty("type", "ServiceClient")
                    .withProperty("serviceType", "discovery")
                    .build();
            discoveryBalancer = new HttpServiceBalancerImpl("discovery", reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, name));
        }
        return discoveryBalancer;
    }

    private static class DiscoveryExecutorProvider
            implements Provider<ScheduledExecutorService>
    {
        private ScheduledExecutorService executor;

        @Override
        public ScheduledExecutorService get()
        {
            checkState(executor == null, "provider already used");
            executor = new ScheduledThreadPoolExecutor(5, daemonThreadsNamed("Discovery-%s"));
            return executor;
        }

        @PreDestroy
        public void destroy()
        {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }
}

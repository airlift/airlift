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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ForDiscoveryClient;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.ServiceSelectorFactory;
import com.proofpoint.discovery.client.balance.HttpServiceBalancerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class TestingDiscoveryModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        // bind discovery client and dependencies
        binder.bind(InMemoryDiscoveryClient.class).in(Scopes.SINGLETON);
        binder.bind(DiscoveryAnnouncementClient.class).to(Key.get(InMemoryDiscoveryClient.class)).in(Scopes.SINGLETON);
        binder.bind(DiscoveryLookupClient.class).to(Key.get(InMemoryDiscoveryClient.class)).in(Scopes.SINGLETON);

        // bind announcer
        binder.bind(Announcer.class).in(Scopes.SINGLETON);
        // Must create a multibinder for service announcements or construction will fail if no
        // service announcements are bound, which is legal for processes that don't have public services
        Multibinder.newSetBinder(binder, ServiceAnnouncement.class);

        binder.bind(ServiceSelectorFactory.class).to(SimpleServiceSelectorFactory.class).in(Scopes.SINGLETON);
        binder.bind(HttpServiceBalancerFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @ForDiscoveryClient
    public ScheduledExecutorService createDiscoveryExecutor()
    {
        return new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
    }
}

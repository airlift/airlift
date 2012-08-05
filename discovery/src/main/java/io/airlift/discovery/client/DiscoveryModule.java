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
package io.airlift.discovery.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import org.weakref.jmx.guice.MBeanModule;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;

public class DiscoveryModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        // bind service inventory
        binder.bind(ServiceInventory.class).in(Scopes.SINGLETON);
        bindConfig(binder).to(ServiceInventoryConfig.class);

        // for legacy configurations
        bindConfig(binder).to(DiscoveryClientConfig.class);

        // bind discovery client and dependencies
        binder.bind(DiscoveryLookupClient.class).to(HttpDiscoveryLookupClient.class).in(Scopes.SINGLETON);
        binder.bind(DiscoveryAnnouncementClient.class).to(HttpDiscoveryAnnouncementClient.class).in(Scopes.SINGLETON);
        jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

        // bind the http client
        httpClientBinder(binder).bindAsyncHttpClient("discovery", ForDiscoveryClient.class);
        httpClientBinder(binder).bindHttpClient("discovery", ForDiscoveryClient.class);

        // bind announcer
        binder.bind(Announcer.class).in(Scopes.SINGLETON);
        // Must create a multibinder for service announcements or construction will fail if no
        // service announcements are bound, which is legal for processes that don't have public services
        Multibinder.newSetBinder(binder, ServiceAnnouncement.class);

        binder.bind(ServiceSelectorFactory.class).to(CachingServiceSelectorFactory.class).in(Scopes.SINGLETON);

        MBeanModule.newExporter(binder).export(ServiceInventory.class).withGeneratedName();
    }

    @Provides
    @ForDiscoveryClient
    public ScheduledExecutorService createDiscoveryExecutor()
    {
        return new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
    }

    @Provides
    @ForDiscoveryClient
    public URI getDiscoveryUri(ServiceInventory serviceInventory, DiscoveryClientConfig config)
    {
        Iterable<ServiceDescriptor> discovery = serviceInventory.getServiceDescriptors("discovery");
        for (ServiceDescriptor descriptor : discovery) {
            if (descriptor.getState() != ServiceState.RUNNING) {
                continue;
            }

            try {
                return new URI(descriptor.getProperties().get("https"));
            } catch (Exception ignored) {
            }
            try {
                return new URI(descriptor.getProperties().get("http"));
            } catch (Exception ignored) {
            }
        }
        if (config != null) {
            return config.getDiscoveryServiceURI();
        }
        return null;
    }
}

package com.proofpoint.discovery.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.http.client.AsyncHttpClientModule;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.json.JsonCodecBinder.jsonCodecBinder;

public class DiscoveryModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        // bind service inventory
        binder.bind(HttpServiceInventory.class).in(Scopes.SINGLETON);
        bindConfig(binder).to(ServiceInventoryConfig.class);

        // for legacy configurations
        bindConfig(binder).to(DiscoveryClientConfig.class);

        // bind discovery client and dependencies
        binder.bind(DiscoveryLookupClient.class).to(HttpDiscoveryLookupClient.class).in(Scopes.SINGLETON);
        binder.bind(DiscoveryAnnouncementClient.class).to(HttpDiscoveryAnnouncementClient.class).in(Scopes.SINGLETON);
        jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

        // bind the http client
        binder.install(new AsyncHttpClientModule("discovery", ForDiscoveryClient.class));

        // bind announcer
        binder.bind(Announcer.class).in(Scopes.SINGLETON);
        // Must create a multibinder for service announcements or construction will fail if no
        // service announcements are bound, which is legal for processes that don't have public services
        Multibinder.newSetBinder(binder, ServiceAnnouncement.class);

        binder.bind(ServiceSelectorFactory.class).to(CachingServiceSelectorFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @ForDiscoveryClient
    public ScheduledExecutorService createDiscoveryExecutor()
    {
        return new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
    }

    @Provides
    @ForDiscoveryClient
    public URI getDiscoveryUri(HttpServiceInventory serviceInventory, DiscoveryClientConfig config)
    {
        Iterable<ServiceDescriptor> discovery = serviceInventory.getServiceDescriptors("discovery");
        for (ServiceDescriptor descriptor : discovery) {
            if (descriptor.getState() != ServiceState.RUNNING) {
                continue;
            }

            try {
                return new URI(descriptor.getProperties().get("https"));
            } catch (Exception e) {
            }
            try {
                return new URI(descriptor.getProperties().get("http"));
            } catch (Exception e) {
            }
        }
        if (config != null) {
            return config.getDiscoveryServiceURI();
        }
        return null;
    }
}

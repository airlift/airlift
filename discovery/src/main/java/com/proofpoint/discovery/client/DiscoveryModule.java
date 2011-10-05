package com.proofpoint.discovery.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.HttpDiscoveryClient.ServiceDescriptorsRepresentation;
import com.proofpoint.http.client.HttpClientModule;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.json.JsonCodecBinder.jsonCodecBinder;

public class DiscoveryModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        // bind discovery client and dependencies
        binder.bind(DiscoveryClient.class).to(HttpDiscoveryClient.class).in(Scopes.SINGLETON);
        bindConfig(binder).to(DiscoveryClientConfig.class);
        jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

        // bind the http client
        binder.install(new HttpClientModule("discovery", ForDiscoveryClient.class));

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
        return new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
    }
}

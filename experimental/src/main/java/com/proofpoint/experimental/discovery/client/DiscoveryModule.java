package com.proofpoint.experimental.discovery.client;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.proofpoint.experimental.discovery.client.DiscoveryClient.ServiceDescriptorsRepresentation;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.experimental.json.JsonCodecBinder.jsonCodecBinder;

public class DiscoveryModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(DiscoveryClient.class).in(Scopes.SINGLETON);
        jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

        binder.bind(Announcer.class).in(Scopes.SINGLETON);

        bindConfig(binder).to(DiscoveryClientConfig.class);
    }

    @Provides
    @ForDiscoverClient
    public ScheduledExecutorService createDiscoveryExecutor()
    {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").build());
        return MoreExecutors.getExitingScheduledExecutorService(executor);
    }
}

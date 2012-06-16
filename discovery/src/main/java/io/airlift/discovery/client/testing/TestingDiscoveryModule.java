package io.airlift.discovery.client.testing;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import io.airlift.discovery.client.Announcer;
import io.airlift.discovery.client.DiscoveryAnnouncementClient;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ForDiscoveryClient;
import io.airlift.discovery.client.ServiceAnnouncement;
import io.airlift.discovery.client.ServiceSelectorFactory;

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
    }

    @Provides
    @ForDiscoveryClient
    public ScheduledExecutorService createDiscoveryExecutor()
    {
        return new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
    }
}

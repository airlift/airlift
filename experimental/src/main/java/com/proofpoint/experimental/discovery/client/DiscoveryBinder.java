package com.proofpoint.experimental.discovery.client;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;

import static com.proofpoint.experimental.discovery.client.ServiceTypeFactory.serviceType;

public class DiscoveryBinder
{
    public static DiscoveryBinder discoveryBinder(Binder binder)
    {
        return new DiscoveryBinder(binder);
    }

    private final Multibinder<ServiceAnnouncement> serviceAnnouncementBinder;
    private final Binder binder;

    private DiscoveryBinder(Binder binder)
    {
        this.binder = binder;
        this.serviceAnnouncementBinder = Multibinder.newSetBinder(binder, ServiceAnnouncement.class);
    }

    public void bindSelector(String type)
    {
        bindSelector(serviceType(type));
    }

    public void bindSelector(String type, String pool)
    {
        bindSelector(serviceType(type, pool));
    }

    public void bindSelector(ServiceType serviceType)
    {
        binder.bind(ServiceSelector.class).annotatedWith(serviceType).toProvider(new ServiceSelectorProvider(serviceType));
    }

    public void bindServiceAnnouncement(ServiceAnnouncement announcement)
    {
        serviceAnnouncementBinder.addBinding().toInstance(announcement);
    }

    public void bindServiceAnnouncement(Provider<ServiceAnnouncement> announcementProvider)
    {
        serviceAnnouncementBinder.addBinding().toProvider(announcementProvider);
    }

    public <T extends ServiceAnnouncement> void bindServiceAnnouncement(Class<? extends Provider<T>> announcementProviderClass)
    {
        serviceAnnouncementBinder.addBinding().toProvider(announcementProviderClass);
    }
}

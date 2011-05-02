package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.experimental.discovery.client.ServiceTypeFactory.serviceType;

public class DiscoveryBinder
{
    public static DiscoveryBinder discoveryBinder(Binder binder)
    {
        Preconditions.checkNotNull(binder, "binder is null");
        return new DiscoveryBinder(binder);
    }

    private final Multibinder<ServiceAnnouncement> serviceAnnouncementBinder;
    private final Binder binder;

    private DiscoveryBinder(Binder binder)
    {
        Preconditions.checkNotNull(binder, "binder is null");
        this.binder = binder;
        this.serviceAnnouncementBinder = Multibinder.newSetBinder(binder, ServiceAnnouncement.class);
    }

    public void bindSelector(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        bindSelector(serviceType(type));
    }

    public void bindSelector(ServiceType serviceType)
    {
        Preconditions.checkNotNull(serviceType, "serviceType is null");
        bindConfig(binder).annotatedWith(serviceType).prefixedWith("discovery." + serviceType.value()).to(ServiceSelectorConfig.class);
        binder.bind(ServiceSelector.class).annotatedWith(serviceType).toProvider(new ServiceSelectorProvider(serviceType.value()));
    }

    public void bindServiceAnnouncement(ServiceAnnouncement announcement)
    {
        Preconditions.checkNotNull(announcement, "announcement is null");
        serviceAnnouncementBinder.addBinding().toInstance(announcement);
    }

    public void bindServiceAnnouncement(Provider<ServiceAnnouncement> announcementProvider)
    {
        Preconditions.checkNotNull(announcementProvider, "announcementProvider is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProvider);
    }

    public <T extends ServiceAnnouncement> void bindServiceAnnouncement(Class<? extends Provider<T>> announcementProviderClass)
    {
        Preconditions.checkNotNull(announcementProviderClass, "announcementProviderClass is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProviderClass);
    }
}

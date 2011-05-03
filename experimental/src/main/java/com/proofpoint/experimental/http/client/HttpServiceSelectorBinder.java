package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.proofpoint.experimental.discovery.client.DiscoveryBinder;
import com.proofpoint.experimental.discovery.client.ServiceType;

import static com.proofpoint.experimental.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.experimental.discovery.client.ServiceTypes.serviceType;

public class HttpServiceSelectorBinder
{
    public static HttpServiceSelectorBinder httpServiceSelectorBinder(Binder binder)
    {
        return new HttpServiceSelectorBinder(binder);
    }

    private final Binder binder;
    private final DiscoveryBinder discoveryBinder;

    private HttpServiceSelectorBinder(Binder binder)
    {
        this.binder = binder;
        discoveryBinder = discoveryBinder(binder);
    }

    public void bindSelector(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        bindSelector(serviceType(type));
    }

    public void bindSelector(ServiceType serviceType)
    {
        Preconditions.checkNotNull(serviceType, "serviceType is null");
        discoveryBinder.bindSelector(serviceType);
        binder.bind(HttpServiceSelector.class).annotatedWith(serviceType).toProvider(new HttpServiceSelectorProvider(serviceType.value()));
    }
}

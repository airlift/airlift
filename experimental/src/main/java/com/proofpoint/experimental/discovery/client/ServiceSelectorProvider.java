package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.concurrent.ScheduledExecutorService;

public class ServiceSelectorProvider
        implements Provider<ServiceSelector>
{
    private final ServiceType type;
    private DiscoveryClient client;
    private ScheduledExecutorService executor;

    public ServiceSelectorProvider(ServiceType type)
    {
        Preconditions.checkNotNull(type);
        this.type = type;
    }

    @Inject
    public void setClient(DiscoveryClient client)
    {
        Preconditions.checkNotNull(client, "client is null");
        this.client = client;
    }

    @Inject
    public void setExecutor(@ForDiscoverClient ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(executor, "executor is null");
        this.executor = executor;
    }

    public ServiceSelector get()
    {
        Preconditions.checkState(client != null);
        Preconditions.checkNotNull(executor, "executor is null");
        ServiceSelectorImpl serviceSelector = new ServiceSelectorImpl(type, client, executor);
        serviceSelector.start();
        return serviceSelector;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServiceSelectorProvider that = (ServiceSelectorProvider) o;

        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return type.hashCode();
    }
}

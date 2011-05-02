package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;

import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.experimental.discovery.client.ServiceTypeFactory.serviceType;

public class ServiceSelectorProvider
        implements Provider<ServiceSelector>
{
    private final String type;
    private DiscoveryClient client;
    private ScheduledExecutorService executor;
    private Injector injector;

    public ServiceSelectorProvider(String type)
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

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    public ServiceSelector get()
    {
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(injector, "injector is null");

        ServiceSelectorConfig selectorConfig = injector.getInstance(Key.get(ServiceSelectorConfig.class, serviceType(type)));

        ServiceSelectorImpl serviceSelector = new ServiceSelectorImpl(type, selectorConfig, client, executor);
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

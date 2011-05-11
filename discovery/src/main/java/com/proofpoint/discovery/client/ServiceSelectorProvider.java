package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;

import static com.proofpoint.discovery.client.ServiceTypes.serviceType;

public class ServiceSelectorProvider
        implements Provider<ServiceSelector>
{
    private final String type;
    private ServiceSelectorFactory serviceSelectorFactory;
    private Injector injector;

    public ServiceSelectorProvider(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        this.type = type;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        Preconditions.checkNotNull(injector, "injector is null");
        this.injector = injector;
    }

    @Inject
    public void setServiceSelectorFactory(ServiceSelectorFactory serviceSelectorFactory)
    {
        Preconditions.checkNotNull(serviceSelectorFactory, "serviceSelectorFactory is null");
        this.serviceSelectorFactory = serviceSelectorFactory;
    }

    public ServiceSelector get()
    {
        Preconditions.checkNotNull(serviceSelectorFactory, "serviceSelectorFactory is null");
        Preconditions.checkNotNull(injector, "injector is null");

        ServiceSelectorConfig selectorConfig = injector.getInstance(Key.get(ServiceSelectorConfig.class, serviceType(type)));

        ServiceSelector serviceSelector = serviceSelectorFactory.createServiceSelector(type, selectorConfig);
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

package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.proofpoint.experimental.discovery.client.ServiceSelector;
import com.proofpoint.experimental.discovery.client.ServiceType;

public class HttpServiceSelectorProvider
        implements Provider<HttpServiceSelector>
{
    private final ServiceType type;
    private Injector injector;

    public HttpServiceSelectorProvider(ServiceType type)
    {
        Preconditions.checkNotNull(type);
        this.type = type;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    public HttpServiceSelector get()
    {
        Preconditions.checkNotNull(injector, "injector is null");
        ServiceSelector serviceSelector = injector.getInstance(Key.get(ServiceSelector.class, type));
        HttpServiceSelector httpServiceSelector = new HttpServiceSelector(type, serviceSelector);
        return httpServiceSelector;
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

        HttpServiceSelectorProvider that = (HttpServiceSelectorProvider) o;

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

package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.proofpoint.experimental.discovery.client.ServiceSelector;

import static com.proofpoint.experimental.discovery.client.ServiceTypes.serviceType;

public class HttpServiceProvider
        implements Provider<HttpServiceSelector>
{
    private final String type;
    private Injector injector;

    public HttpServiceProvider(String type)
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

        ServiceSelector serviceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(type)));

        HttpServiceSelector httpServiceSelector = new HttpServiceSelectorImpl(serviceSelector);
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

        HttpServiceProvider that = (HttpServiceProvider) o;

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

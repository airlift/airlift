package io.airlift.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;

import static io.airlift.discovery.client.ServiceTypes.serviceType;

class HttpServiceSelectorProvider
        implements Provider<HttpServiceSelector>
{
    private final String type;
    private Injector injector;

    public HttpServiceSelectorProvider(String type)
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

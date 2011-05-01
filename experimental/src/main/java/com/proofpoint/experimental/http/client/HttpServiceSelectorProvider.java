package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.experimental.discovery.client.DiscoveryClient;
import com.proofpoint.experimental.discovery.client.ForDiscoverClient;
import com.proofpoint.experimental.discovery.client.ServiceType;

import java.util.concurrent.ScheduledExecutorService;

public class HttpServiceSelectorProvider
        implements Provider<HttpServiceSelector>
{
    private final ServiceType type;
    private DiscoveryClient client;
    private ScheduledExecutorService executor;

    public HttpServiceSelectorProvider(ServiceType type)
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

    public HttpServiceSelector get()
    {
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");

        HttpServiceSelector httpServiceSelector = new HttpServiceSelector(type, client, executor);
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

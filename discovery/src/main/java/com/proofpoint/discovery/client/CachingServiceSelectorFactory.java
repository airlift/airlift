package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.inject.Inject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

public class CachingServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryClient client;
    private final ScheduledExecutorService executor;

    @Inject
    public CachingServiceSelectorFactory(DiscoveryClient client, @ForDiscoveryClient ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");
        this.client = client;
        this.executor = executor;
    }

    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");

        CachingServiceSelector serviceSelector = new CachingServiceSelector(type, selectorConfig, client, executor);
        try {
            serviceSelector.start();
        }
        catch (TimeoutException e) {
            throw Throwables.propagate(e);
        }

        return serviceSelector;
    }
}

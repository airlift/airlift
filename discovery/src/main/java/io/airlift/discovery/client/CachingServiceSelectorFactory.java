package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.inject.Inject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

public class CachingServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryLookupClient lookupClient;
    private final ScheduledExecutorService executor;

    @Inject
    public CachingServiceSelectorFactory(DiscoveryLookupClient lookupClient, @ForDiscoveryClient ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(lookupClient, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");
        this.lookupClient = lookupClient;
        this.executor = executor;
    }

    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");

        CachingServiceSelector serviceSelector = new CachingServiceSelector(type, selectorConfig, lookupClient, executor);
        try {
            serviceSelector.start();
        }
        catch (TimeoutException e) {
            throw Throwables.propagate(e);
        }

        return serviceSelector;
    }
}

package com.proofpoint.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceSelector;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.discovery.client.ServiceSelectorFactory;

public class SimpleServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryLookupClient lookupClient;

    @Inject
    public SimpleServiceSelectorFactory(DiscoveryLookupClient lookupClient)
    {
        Preconditions.checkNotNull(lookupClient, "client is null");
        this.lookupClient = lookupClient;
    }

    @Override
    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");

        return new SimpleServiceSelector(type, selectorConfig, lookupClient);
    }
}

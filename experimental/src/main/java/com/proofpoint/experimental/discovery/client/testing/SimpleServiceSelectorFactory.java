package com.proofpoint.experimental.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.proofpoint.experimental.discovery.client.DiscoveryClient;
import com.proofpoint.experimental.discovery.client.ServiceSelector;
import com.proofpoint.experimental.discovery.client.ServiceSelectorConfig;
import com.proofpoint.experimental.discovery.client.ServiceSelectorFactory;

public class SimpleServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryClient client;

    @Inject
    public SimpleServiceSelectorFactory(DiscoveryClient client)
    {
        Preconditions.checkNotNull(client, "client is null");
        this.client = client;
    }

    @Override
    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");

        return new SimpleServiceSelector(type, selectorConfig, client);
    }
}

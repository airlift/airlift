package io.airlift.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ServiceSelector;
import io.airlift.discovery.client.ServiceSelectorConfig;
import io.airlift.discovery.client.ServiceSelectorFactory;

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

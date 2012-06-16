package io.airlift.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.airlift.discovery.client.DiscoveryException;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceDescriptors;
import io.airlift.discovery.client.ServiceSelector;
import io.airlift.discovery.client.ServiceSelectorConfig;
import io.airlift.log.Logger;

import java.util.List;

public class SimpleServiceSelector implements ServiceSelector
{
    private final static Logger log = Logger.get(SimpleServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryLookupClient lookupClient;

    public SimpleServiceSelector(String type, ServiceSelectorConfig selectorConfig, DiscoveryLookupClient lookupClient)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");
        Preconditions.checkNotNull(lookupClient, "client is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.lookupClient = lookupClient;
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String getPool()
    {
        return pool;
    }

    @Override
    public List<ServiceDescriptor> selectAllServices()
    {
        try {
            ServiceDescriptors serviceDescriptors = lookupClient.getServices(type, pool).checkedGet();
            return serviceDescriptors.getServiceDescriptors();
        }
        catch (DiscoveryException e) {
            log.error(e);
            return ImmutableList.of();
        }
    }
}

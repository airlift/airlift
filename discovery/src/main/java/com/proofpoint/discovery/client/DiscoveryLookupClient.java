package com.proofpoint.discovery.client;

import com.google.common.util.concurrent.CheckedFuture;

public interface DiscoveryLookupClient
{
    CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type, String pool);

    CheckedFuture<ServiceDescriptors, DiscoveryException> refreshServices(ServiceDescriptors serviceDescriptors);
}

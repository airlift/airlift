package com.proofpoint.experimental.discovery.client;

import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.units.Duration;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.experimental.discovery.client.ServiceTypeFactory.serviceType;
import static java.lang.String.format;

public interface DiscoveryClient
{
    Duration DEFAULT_DELAY = new Duration(10, TimeUnit.SECONDS);

    CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services);

    CheckedFuture<Void, DiscoveryException> unannounce();

    CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type, String pool);

    CheckedFuture<ServiceDescriptors, DiscoveryException> refreshServices(ServiceDescriptors serviceDescriptors);

}

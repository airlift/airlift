package io.airlift.discovery.client;

import com.google.common.util.concurrent.CheckedFuture;
import io.airlift.units.Duration;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface DiscoveryAnnouncementClient
{
    Duration DEFAULT_DELAY = new Duration(10, TimeUnit.SECONDS);

    CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services);

    CheckedFuture<Void, DiscoveryException> unannounce();
}

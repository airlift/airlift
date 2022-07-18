/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.concurrent.ThreadPoolExecutorMBean;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.concurrent.MoreFutures.getFutureValue;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Announcer
{
    private static final Logger log = Logger.get(Announcer.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryAnnouncementClient announcementClient;
    private final ScheduledExecutorService executor;
    private final ThreadPoolExecutorMBean executorMBean;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final ExponentialBackOff errorBackOff = new ExponentialBackOff(
            new Duration(1, MILLISECONDS),
            new Duration(1, SECONDS),
            "Discovery server connect succeeded for announce",
            "Cannot connect to discovery server for announce",
            log);

    @Inject
    public Announcer(DiscoveryAnnouncementClient announcementClient, Set<ServiceAnnouncement> serviceAnnouncements)
    {
        requireNonNull(announcementClient, "client is null");
        requireNonNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.announcementClient = announcementClient;
        serviceAnnouncements.forEach(this::addServiceAnnouncement);
        executor = new ScheduledThreadPoolExecutor(5, daemonThreadsNamed("Announcer-%s"));
        executorMBean = new ThreadPoolExecutorMBean((ThreadPoolExecutor) executor);
    }

    @Managed
    @Nested
    public ThreadPoolExecutorMBean getExecutor()
    {
        return executorMBean;
    }

    public void start()
    {
        checkState(!executor.isShutdown(), "Announcer has been destroyed");
        if (started.compareAndSet(false, true)) {
            // announce immediately, if discovery is running
            ListenableFuture<Duration> announce = announce(System.nanoTime(), new Duration(0, SECONDS));
            try {
                announce.get(30, SECONDS);
            }
            catch (Exception ignored) {
            }
        }
    }

    @PreDestroy
    public void destroy()
    {
        executor.shutdownNow();
        try {
            executor.awaitTermination(30, SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // unannounce
        try {
            getFutureValue(announcementClient.unannounce(), DiscoveryException.class);
        }
        catch (DiscoveryException e) {
            if (e.getCause() instanceof ConnectException) {
                log.error("Cannot connect to discovery server for unannounce: %s", e.getCause().getMessage());
            }
            else {
                log.error(e);
            }
        }
    }

    public void addServiceAnnouncement(ServiceAnnouncement serviceAnnouncement)
    {
        requireNonNull(serviceAnnouncement, "serviceAnnouncement is null");
        announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
    }

    public void removeServiceAnnouncement(UUID serviceId)
    {
        announcements.remove(serviceId);
    }

    public Set<ServiceAnnouncement> getServiceAnnouncements()
    {
        return ImmutableSet.copyOf(announcements.values());
    }

    private ListenableFuture<Duration> announce(long delayStart, Duration expectedDelay)
    {
        // log announcement did not happen within 5 seconds of expected delay
        if (System.nanoTime() - (delayStart + expectedDelay.roundTo(NANOSECONDS)) > SECONDS.toNanos(5)) {
            log.error("Expected service announcement after %s, but announcement was delayed %s", expectedDelay, Duration.nanosSince(delayStart));
        }

        long requestStart = System.nanoTime();
        ListenableFuture<Duration> future = announcementClient.announce(getServiceAnnouncements());

        Futures.addCallback(future, new FutureCallback<Duration>()
        {
            @Override
            public void onSuccess(Duration expectedDelay)
            {
                errorBackOff.success();

                // wait 80% of the suggested delay
                expectedDelay = new Duration(expectedDelay.toMillis() * 0.8, MILLISECONDS);
                scheduleNextAnnouncement(expectedDelay);
            }

            @Override
            public void onFailure(Throwable t)
            {
                Duration duration = errorBackOff.failed(t);
                scheduleNextAnnouncement(duration);
            }
        }, executor);

        return future;
    }

    public ListenableFuture<?> forceAnnounce()
    {
        return announcementClient.announce(getServiceAnnouncements());
    }

    private void scheduleNextAnnouncement(Duration expectedDelay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }

        long delayStart = System.nanoTime();
        executor.schedule(() -> announce(delayStart, expectedDelay), expectedDelay.toMillis(), MILLISECONDS);
    }
}

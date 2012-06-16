package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import javax.annotation.PreDestroy;
import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.proofpoint.discovery.client.DiscoveryAnnouncementClient.DEFAULT_DELAY;

public class Announcer
{
    private final static Logger log = Logger.get(Announcer.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryAnnouncementClient announcementClient;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private final AtomicBoolean started = new AtomicBoolean(false);


    @Inject
    public Announcer(DiscoveryAnnouncementClient announcementClient, Set<ServiceAnnouncement> serviceAnnouncements)
    {
        Preconditions.checkNotNull(announcementClient, "client is null");
        Preconditions.checkNotNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.announcementClient = announcementClient;
        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
        }
        executor = new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Announcer-%s").setDaemon(true).build());
    }

    public void start()
            throws TimeoutException
    {
        Preconditions.checkState(!executor.isShutdown(), "Announcer has been destroyed");
        if (started.compareAndSet(false, true)) {
            // announce immediately, if discovery is running
            try {
                announce().checkedGet(30, TimeUnit.SECONDS);
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
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // unannounce
        try {
            announcementClient.unannounce().checkedGet();
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
        Preconditions.checkNotNull(serviceAnnouncement, "serviceAnnouncement is null");
        announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
    }

    public void removeServiceAnnouncement(UUID serviceId)
    {
        announcements.remove(serviceId);
    }

    private CheckedFuture<Duration, DiscoveryException> announce()
    {
        final CheckedFuture<Duration, DiscoveryException> future = announcementClient.announce(ImmutableSet.copyOf(announcements.values()));

        future.addListener(new Runnable()
        {
            @Override
            public void run()
            {
                Duration duration = DEFAULT_DELAY;
                try {
                    duration = future.checkedGet();
                    if (serverUp.compareAndSet(false, true)) {
                        log.info("Discovery server connect succeeded for announce");
                    }
                }
                catch (DiscoveryException e) {
                    if (serverUp.compareAndSet(true, false)) {
                        log.error("Cannot connect to discovery server for announce: %s", e.getMessage());
                    }
                    log.debug(e, "Cannot connect to discovery server for announce");
                }
                finally {
                    scheduleNextAnnouncement(duration);
                }
            }
        }, executor);

        return future;
    }

    private void scheduleNextAnnouncement(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(new Runnable() {
            @Override
            public void run()
            {
                announce();
            }
        }, (long) (delay.toMillis() * 0.8), TimeUnit.MILLISECONDS);
    }
}

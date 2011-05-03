package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class Announcer
{
    private final static Logger log = Logger.get(Announcer.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryClient client;
    private final ScheduledExecutorService executor;
    private final AtomicLong currentJob = new AtomicLong();

    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledFuture<?> restartAnnouncementSchedule;

    @Inject
    public Announcer(DiscoveryClient client, Set<ServiceAnnouncement> serviceAnnouncements)
    {
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.client = client;
        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
        }
        executor = MoreExecutors.getExitingScheduledExecutorService(new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder().setNameFormat("Announcer-%s").build()));
    }

    public void start()
    {
        lock.lock();
        try {
            // already destroyed?
            Preconditions.checkState(!executor.isShutdown(), "Announcer has been destroyed");

            // already running?
            if (restartAnnouncementSchedule != null) {
                return;
            }

            // make sure update runs at least every minutes
            // this will help the system restart if a task
            // hangs or dies without being rescheduled
            restartAnnouncementSchedule = executor.scheduleWithFixedDelay(new Runnable()
            {
                @Override
                public void run()
                {
                    announce();
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
        finally {
            lock.unlock();
        }

        // announce immediately
        announce();
    }

    public void destroy()
    {
        // cancel scheduled jobs
        lock.lock();
        try {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        finally {
            lock.unlock();
        }

        // unannounce
        try {
            client.unannounce().checkedGet();
        }
        catch (DiscoveryException e) {
            log.error(e);
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

    private void announce()
    {
        final long jobId = currentJob.get();
        final CheckedFuture<Duration, DiscoveryException> future = client.announce(ImmutableSet.copyOf(announcements.values()));

        future.addListener(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    if (currentJob.compareAndSet(jobId, jobId + 1)) {
                        Duration duration = future.checkedGet();
                        scheduleAnnouncement(duration);
                    }
                }
                catch (DiscoveryException e) {
                    if (Throwables.getRootCause(e) instanceof ConnectException) {
                        log.debug(e, "Can not connect to discovery server");
                    }
                    else {
                        log.error(e);
                    }
                }
            }
        }, executor);
    }

    private void scheduleAnnouncement(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                announce();

                return null;
            }
        }, (long) delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}

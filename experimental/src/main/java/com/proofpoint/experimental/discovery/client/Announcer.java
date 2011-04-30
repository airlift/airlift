package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Announcer
{
    private final static Logger log = Logger.get(Announcer.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryClient client;
    private final ScheduledExecutorService executor;
    private final AtomicLong currentJob = new AtomicLong();

    @Inject
    public Announcer(DiscoveryClient client, @ForDiscoverClient ScheduledExecutorService executor, Set<ServiceAnnouncement> serviceAnnouncements)
    {
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.client = client;
        this.executor = executor;
        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
        }
    }

    public void start()
    {
        // make sure update runs at least every minutes
        // this will help the system restart if a task
        // hangs or dies without being rescheduled
        executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                scheduleAnnouncement(new Duration(0, TimeUnit.SECONDS));
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public void stop()
    {
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

    private void scheduleAnnouncement(Duration delay)
    {
        executor.schedule(new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
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
                            log.error(e);
                        }
                    }
                }, executor);

                return null;
            }
        }, (long) delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}

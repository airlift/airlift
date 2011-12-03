package com.proofpoint.stats;

import com.google.common.annotations.Beta;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Beta
public class CounterStat
{
    private final AtomicLong count = new AtomicLong(0);
    private final EWMA oneMinute = EWMA.oneMinuteEWMA();
    private final EWMA fiveMinute = EWMA.fiveMinuteEWMA();
    private final EWMA fifteenMinute = EWMA.fifteenMinuteEWMA();
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;

    public CounterStat(ScheduledExecutorService executor)
    {
        this.executor = executor;
    }

    @PostConstruct
    public void start()
    {
        future = executor.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                oneMinute.tick();
                fiveMinute.tick();
                fifteenMinute.tick();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop()
    {
        future.cancel(false);
    }

    public void update(long count)
    {
        oneMinute.update(count);
        fiveMinute.update(count);
        fifteenMinute.update(count);
        this.count.addAndGet(count);
    }

    @Managed
    public long getCount()
    {
        return count.get();
    }

    @Managed
    public double getOneMinuteRate()
    {
        return oneMinute.rate(TimeUnit.SECONDS);
    }

    @Managed
    public double getFiveMinuteRate()
    {
        return fiveMinute.rate(TimeUnit.SECONDS);
    }

    @Managed
    public double getFifteenMinuteRate()
    {
        return fifteenMinute.rate(TimeUnit.SECONDS);
    }
}

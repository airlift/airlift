package com.proofpoint.stats;

import com.google.common.annotations.Beta;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.floor;

@Beta
public class MeterStat
{
    private final AtomicLong sum = new AtomicLong(0);
    private final ExponentiallyDecayingSample sample = new ExponentiallyDecayingSample(1028, 0.015);
    private final EWMA oneMinute = EWMA.oneMinuteEWMA();
    private final EWMA fiveMinute = EWMA.fiveMinuteEWMA();
    private final EWMA fifteenMinute = EWMA.fifteenMinuteEWMA();
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;

    public MeterStat(ScheduledExecutorService executor)
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

    public void update(long value)
    {
        sample.update(value);
        oneMinute.update(value);
        fiveMinute.update(value);
        fifteenMinute.update(value);
        sum.incrementAndGet();
    }

    @Managed
    public long getSum()
    {
        return sum.get();
    }

    @Managed
    public long getMin()
    {
        return Collections.min(sample.values());
    }

    @Managed
    public long getMax()
    {
        return Collections.max(sample.values());
    }

    @Managed
    public double getMean()
    {
        List<Long> values = sample.values();

        long sum = 0;
        for (long value : values) {
            sum += value;
        }

        return sum * 1.0 / values.size();
    }

    public double getStdDev()
    {
        throw new UnsupportedOperationException("not yet implemented");
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

    @Managed
    public double getTP50()
    {
        return sample.percentiles(0.5)[0];
    }

    @Managed
    public double getTP90()
    {
        return sample.percentiles(0.9)[0];
    }

    @Managed
    public double getTP99()
    {
        return sample.percentiles(0.99)[0];
    }

    @Managed
    public double getTP999()
    {
        return sample.percentiles(0.999)[0];
    }
}

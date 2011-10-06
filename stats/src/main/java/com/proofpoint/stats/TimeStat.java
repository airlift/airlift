package com.proofpoint.stats;

import org.weakref.jmx.Managed;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.floor;

public class TimeStat
{
    private final AtomicLong sum = new AtomicLong(0);
    private final ExponentiallyDecayingSample sample = new ExponentiallyDecayingSample(1028, 0.015);

    public void update(long value)
    {
        sample.update(value);
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

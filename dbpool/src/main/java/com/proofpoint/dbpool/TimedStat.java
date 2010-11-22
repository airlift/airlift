package com.proofpoint.dbpool;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.weakref.jmx.Managed;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TimedStat
{
    private final DescriptiveStatistics timedStatistics;
    private final AtomicLong count = new AtomicLong();

    public TimedStat()
    {
        this(5000);
    }

    public TimedStat(int windowSize)
    {
        timedStatistics = new SynchronizedDescriptiveStatistics(windowSize);
    }

    @Managed
    public long getCount()
    {
        return count.get();
    }

    @Managed
    public double getMin()
    {
        return timedStatistics.getMin();
    }

    @Managed
    public double getMax()
    {
        return timedStatistics.getMax();
    }

    @Managed
    public double getMean()
    {
        return timedStatistics.getMean();
    }

    @Managed
    public double getPercentile(double percentile)
    {
        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        return timedStatistics.getPercentile(percentile * 100);
    }

    @Managed(description = "50th Percentile Measurement")
    public double getTP50()
    {
        return timedStatistics.getPercentile(50);
    }

    @Managed(description = "90th Percentile Measurement")
    public double getTP90()
    {
        return timedStatistics.getPercentile(90);
    }

    @Managed(description = "99th Percentile Measurement")
    public double getTP99()
    {
        return timedStatistics.getPercentile(99);
    }

    @Managed(description = "99.9th Percentile Measurement")
    public double getTP999()
    {
        return timedStatistics.getPercentile(99.9);
    }

    public void addValue(double value, TimeUnit timeUnit)
    {
        addValue(new Duration(value, timeUnit));
    }

    public void addValue(Duration duration)
    {
        timedStatistics.addValue(duration.toMillis());
        count.incrementAndGet();
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        long start = System.nanoTime();
        T result = callable.call();
        addValue(Duration.nanosSince(start));
        return result;
    }
}

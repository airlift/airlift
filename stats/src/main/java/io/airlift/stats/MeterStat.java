package io.airlift.stats;

import com.google.common.annotations.Beta;
import org.weakref.jmx.Managed;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Beta
public class MeterStat
{
    private final AtomicLong sum = new AtomicLong(0);
    private final ExponentiallyDecayingSample sample = new ExponentiallyDecayingSample(1028, 0.015);
    private final DecayCounter oneMinute = new DecayCounter(ExponentialDecay.oneMinute());
    private final DecayCounter fiveMinute = new DecayCounter(ExponentialDecay.fiveMinutes());
    private final DecayCounter fifteenMinute = new DecayCounter(ExponentialDecay.fifteenMinutes());

    public void update(long value)
    {
        sample.update(value);
        oneMinute.add(value);
        fiveMinute.add(value);
        fifteenMinute.add(value);
        sum.addAndGet(value);
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
        return oneMinute.getCount();
    }

    @Managed
    public double getFiveMinuteRate()
    {
        return fiveMinute.getCount();
    }

    @Managed
    public double getFifteenMinuteRate()
    {
        return fifteenMinute.getCount();
    }

    @Managed(description = "50th Percentile Measurement")
    public double getTP50()
    {
        return sample.percentiles(0.5)[0];
    }

    @Managed(description = "90th Percentile Measurement")
    public double getTP90()
    {
        return sample.percentiles(0.9)[0];
    }

    @Managed(description = "99th Percentile Measurement")
    public double getTP99()
    {
        return sample.percentiles(0.99)[0];
    }

    @Managed(description = "99.9th Percentile Measurement")
    public double getTP999()
    {
        return sample.percentiles(0.999)[0];
    }
}

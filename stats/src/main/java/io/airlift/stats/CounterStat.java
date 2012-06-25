package io.airlift.stats;

import com.google.common.annotations.Beta;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.AtomicLong;

@Beta
public class CounterStat
{
    private final AtomicLong count = new AtomicLong(0);
    private final DecayCounter oneMinute = new DecayCounter(ExponentialDecay.oneMinute());
    private final DecayCounter fiveMinute = new DecayCounter(ExponentialDecay.fiveMinutes());
    private final DecayCounter fifteenMinute = new DecayCounter(ExponentialDecay.fifteenMinutes());

    public void update(long count)
    {
        oneMinute.add(count);
        fiveMinute.add(count);
        fifteenMinute.add(count);
        this.count.addAndGet(count);
    }

    @Managed
    public long getTotalCount()
    {
        return count.get();
    }

    @Managed
    @Nested
    public DecayCounter getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    public DecayCounter getFiveMinute()
    {
        return fiveMinute;
    }

    @Managed
    @Nested
    public DecayCounter getFifteenMinute()
    {
        return fifteenMinute;
    }
}

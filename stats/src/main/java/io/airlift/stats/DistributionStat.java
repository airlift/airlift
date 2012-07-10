package io.airlift.stats;

import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

public class DistributionStat
{
    private final Distribution oneMinute;
    private final Distribution fiveMinutes;
    private final Distribution fifteenMinutes;
    private final Distribution allTime;

    public DistributionStat()
    {
        oneMinute = new Distribution(ExponentialDecay.oneMinute());
        fiveMinutes = new Distribution(ExponentialDecay.fiveMinutes());
        fifteenMinutes = new Distribution(ExponentialDecay.fifteenMinutes());
        allTime = new Distribution();
    }

    public void add(long value)
    {
        oneMinute.add(value);
        fiveMinutes.add(value);
        fifteenMinutes.add(value);
        allTime.add(value);
    }

    @Managed
    @Nested
    public Distribution getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    public Distribution getFiveMinutes()
    {
        return fiveMinutes;
    }

    @Managed
    @Nested
    public Distribution getFifteenMinutes()
    {
        return fifteenMinutes;
    }

    @Managed
    @Nested
    public Distribution getAllTime()
    {
        return allTime;
    }
}

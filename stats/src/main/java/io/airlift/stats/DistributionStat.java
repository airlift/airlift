package io.airlift.stats;

import io.airlift.stats.Distribution.DistributionSnapshot;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import static java.util.Objects.requireNonNull;

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

    public void add(long value, long count)
    {
        oneMinute.add(value, count);
        fiveMinutes.add(value, count);
        fifteenMinutes.add(value, count);
        allTime.add(value, count);
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

    public DistributionStatSnapshot snapshot()
    {
        return new DistributionStatSnapshot(
                getOneMinute().snapshot(),
                getFiveMinutes().snapshot(),
                getFifteenMinutes().snapshot(),
                getAllTime().snapshot());
    }

    public record DistributionStatSnapshot(
            DistributionSnapshot oneMinute,
            DistributionSnapshot fiveMinute,
            DistributionSnapshot fifteenMinute,
            DistributionSnapshot allTime)
    {
        public DistributionStatSnapshot
        {
            requireNonNull(oneMinute, "oneMinute is null");
            requireNonNull(fiveMinute, "fiveMinute is null");
            requireNonNull(fifteenMinute, "fifteenMinute is null");
            requireNonNull(allTime, "allTime is null");
        }
    }
}

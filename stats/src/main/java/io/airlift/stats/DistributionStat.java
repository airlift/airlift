package io.airlift.stats;

import io.airlift.stats.Distribution.DistributionSnapshot;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.Optional;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static java.util.Objects.requireNonNull;

public class DistributionStat
{
    @Nullable
    private final Distribution oneMinute;
    @Nullable
    private final Distribution fiveMinutes;
    @Nullable
    private final Distribution fifteenMinutes;
    private final Distribution allTime;

    public DistributionStat()
    {
        if (StatsBackendFactory.getBackend() == AIRLIFT) {
            oneMinute = new Distribution(DecayConfig.oneMinute());
            fiveMinutes = new Distribution(DecayConfig.fiveMinutes());
            fifteenMinutes = new Distribution(DecayConfig.fifteenMinutes());
        }
        else {
            oneMinute = null;
            fiveMinutes = null;
            fifteenMinutes = null;
        }
        allTime = new Distribution();
    }

    public void add(long value)
    {
        if (oneMinute != null && fiveMinutes != null && fifteenMinutes != null) {
            oneMinute.add(value);
            fiveMinutes.add(value);
            fifteenMinutes.add(value);
        }
        allTime.add(value);
    }

    public void add(long value, long count)
    {
        if (oneMinute != null && fiveMinutes != null && fifteenMinutes != null) {
            oneMinute.add(value, count);
            fiveMinutes.add(value, count);
            fifteenMinutes.add(value, count);
        }
        allTime.add(value, count);
    }

    @Managed
    @Nested
    @Nullable
    public Distribution getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    @Nullable
    public Distribution getFiveMinutes()
    {
        return fiveMinutes;
    }

    @Managed
    @Nested
    @Nullable
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
                oneMinute == null ? null : oneMinute.snapshot(),
                fiveMinutes == null ? null : fiveMinutes.snapshot(),
                fifteenMinutes == null ? null : fifteenMinutes.snapshot(),
                getAllTime().snapshot());
    }

    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return getAllTime().exponentialHistogramSnapshot();
    }

    public record DistributionStatSnapshot(
            @Nullable
            DistributionSnapshot oneMinute,
            @Nullable
            DistributionSnapshot fiveMinute,
            @Nullable
            DistributionSnapshot fifteenMinute,
            DistributionSnapshot allTime)
    {
        public DistributionStatSnapshot
        {
            requireNonNull(allTime, "allTime is null");
        }
    }
}

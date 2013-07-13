package com.proofpoint.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.proofpoint.reporting.Reported;
import com.proofpoint.stats.Distribution.DistributionSnapshot;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

public class DistributionStat
{
    private final Distribution oneMinute;
    private final Distribution fiveMinutes;
    private final Distribution fifteenMinutes;
    private final Distribution allTime;
    private final BucketedDistribution bucket = new BucketedDistribution();

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
        bucket.add(value);
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

    @Reported
    @Flatten
    public BucketedDistribution getBucket()
    {
        return bucket;
    }

    public DistributionStatSnapshot snapshot()
    {
        return new DistributionStatSnapshot(
                getOneMinute().snapshot(),
                getFiveMinutes().snapshot(),
                getFifteenMinutes().snapshot(),
                getAllTime().snapshot());
    }

    public static class DistributionStatSnapshot
    {
        private final DistributionSnapshot oneMinute;
        private final DistributionSnapshot fiveMinute;
        private final DistributionSnapshot fifteenMinute;
        private final DistributionSnapshot allTime;

        @JsonCreator
        public DistributionStatSnapshot(
                @JsonProperty("oneMinute") DistributionSnapshot oneMinute,
                @JsonProperty("fiveMinute") DistributionSnapshot fiveMinute,
                @JsonProperty("fifteenMinute") DistributionSnapshot fifteenMinute,
                @JsonProperty("allTime") DistributionSnapshot allTime)
        {
            this.oneMinute = oneMinute;
            this.fiveMinute = fiveMinute;
            this.fifteenMinute = fifteenMinute;
            this.allTime = allTime;
        }

        @JsonProperty
        public DistributionSnapshot getOneMinute()
        {
            return oneMinute;
        }

        @JsonProperty
        public DistributionSnapshot getFiveMinutes()
        {
            return fiveMinute;
        }

        @JsonProperty
        public DistributionSnapshot getFifteenMinutes()
        {
            return fifteenMinute;
        }

        @JsonProperty
        public DistributionSnapshot getAllTime()
        {
            return allTime;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("oneMinute", oneMinute)
                    .add("fiveMinute", fiveMinute)
                    .add("fifteenMinute", fifteenMinute)
                    .add("allTime", allTime)
                    .toString();
        }
    }
}

package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    @GuardedBy("this")
    private final DecayTDigest digest;
    @GuardedBy("this")
    private final DecayCounter total;
    private final TimeUnit unit;

    public TimeDistribution()
    {
        this(SECONDS);
    }

    public TimeDistribution(TimeUnit unit)
    {
        requireNonNull(unit, "unit is null");

        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, 0);
        total = new DecayCounter(0);
        this.unit = unit;
    }

    public TimeDistribution(double alpha)
    {
        this(alpha, SECONDS);
    }

    public TimeDistribution(double alpha, TimeUnit unit)
    {
        requireNonNull(unit, "unit is null");

        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        total = new DecayCounter(alpha);
        this.unit = unit;
    }

    public synchronized void add(long value)
    {
        digest.add(value);
        total.add(value);
    }

    @Managed
    public synchronized double getCount()
    {
        return digest.getCount();
    }

    @Managed
    public synchronized double getP50()
    {
        return convertToUnit(digest.valueAt(0.5));
    }

    @Managed
    public synchronized double getP75()
    {
        return convertToUnit(digest.valueAt(0.75));
    }

    @Managed
    public synchronized double getP90()
    {
        return convertToUnit(digest.valueAt(0.90));
    }

    @Managed
    public synchronized double getP95()
    {
        return convertToUnit(digest.valueAt(0.95));
    }

    @Managed
    public synchronized double getP99()
    {
        return convertToUnit(digest.valueAt(0.99));
    }

    @Managed
    public synchronized double getMin()
    {
        return convertToUnit(digest.getMin());
    }

    @Managed
    public synchronized double getMax()
    {
        return convertToUnit(digest.getMax());
    }

    @Managed
    public synchronized double getAvg()
    {
        return convertToUnit(total.getCount()) / getCount();
    }

    @Managed
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        List<Double> percentiles = new ArrayList<>(100);
        for (int i = 0; i < 100; ++i) {
            percentiles.add(i / 100.0);
        }

        List<Double> values;
        synchronized (this) {
            values = digest.valuesAt(percentiles);
        }

        Map<Double, Double> result = new LinkedHashMap<>(values.size());
        for (int i = 0; i < percentiles.size(); ++i) {
            result.put(percentiles.get(i), convertToUnit(values.get(i)));
        }

        return result;
    }

    private double convertToUnit(long nanos)
    {
        if (nanos == Long.MAX_VALUE || nanos == Long.MIN_VALUE) {
            return Double.NaN;
        }
        return nanos * 1.0 / unit.toNanos(1);
    }

    private double convertToUnit(double nanos)
    {
        return nanos * 1.0 / unit.toNanos(1);
    }

    public TimeDistributionSnapshot snapshot()
    {
        return new TimeDistributionSnapshot(
                getCount(),
                getP50(),
                getP75(),
                getP90(),
                getP95(),
                getP99(),
                getMin(),
                getMax(),
                getAvg(),
                getUnit());
    }

    public static class TimeDistributionSnapshot
    {
        private final double count;
        private final double p50;
        private final double p75;
        private final double p90;
        private final double p95;
        private final double p99;
        private final double min;
        private final double max;
        private final double avg;
        private final TimeUnit unit;

        @JsonCreator
        public TimeDistributionSnapshot(
                @JsonProperty("count") double count,
                @JsonProperty("p50") double p50,
                @JsonProperty("p75") double p75,
                @JsonProperty("p90") double p90,
                @JsonProperty("p95") double p95,
                @JsonProperty("p99") double p99,
                @JsonProperty("min") double min,
                @JsonProperty("max") double max,
                @JsonProperty("avg") double avg,
                @JsonProperty("unit") TimeUnit unit)
        {
            this.count = count;
            this.p50 = p50;
            this.p75 = p75;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.unit = unit;
        }

        @JsonProperty
        public double getCount()
        {
            return count;
        }

        @JsonProperty
        public double getP50()
        {
            return p50;
        }

        @JsonProperty
        public double getP75()
        {
            return p75;
        }

        @JsonProperty
        public double getP90()
        {
            return p90;
        }

        @JsonProperty
        public double getP95()
        {
            return p95;
        }

        @JsonProperty
        public double getP99()
        {
            return p99;
        }

        @JsonProperty
        public double getMin()
        {
            return min;
        }

        @JsonProperty
        public double getMax()
        {
            return max;
        }

        @JsonProperty
        public double getAvg()
        {
            return avg;
        }

        @JsonProperty
        public TimeUnit unit()
        {
            return unit;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("count", count)
                    .add("p50", p50)
                    .add("p75", p75)
                    .add("p90", p90)
                    .add("p95", p95)
                    .add("p99", p99)
                    .add("min", min)
                    .add("max", max)
                    .add("avg", avg)
                    .add("unit", unit)
                    .toString();
        }
    }
}

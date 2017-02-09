package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    private static final double MAX_ERROR = 0.01;
    private static final int SEGMENTS = 16;

    private final DecayCounter count;
    private final Object[] locks = new Object[SEGMENTS];
    private final QuantileDigest[] partials = new QuantileDigest[SEGMENTS];

    private final TimeUnit unit;
    private final double alpha;

    @GuardedBy("this")
    private final QuantileDigest merged;
    @GuardedBy("this")
    private long lastMerge;


    public TimeDistribution()
    {
        this(SECONDS);
    }

    public TimeDistribution(TimeUnit unit)
    {
        this(0, unit);
    }

    public TimeDistribution(double alpha)
    {
        this(alpha, SECONDS);
    }

    public TimeDistribution(double alpha, TimeUnit unit)
    {
        Preconditions.checkNotNull(unit, "unit is null");

        this.unit = unit;
        this.alpha = alpha;
        this.count = new DecayCounter(alpha);
        this.merged = new QuantileDigest(MAX_ERROR, alpha);
        for (int i = 0; i < SEGMENTS; i++) {
            locks[i] = new Object();
            partials[i] = new QuantileDigest(MAX_ERROR, alpha);
        }
    }

    public synchronized void add(long value)
    {
        int segment = ThreadLocalRandom.current().nextInt(SEGMENTS);

        synchronized (locks[segment]) {
            partials[segment].add(value);
        }

        count.add(1);
    }

    @Managed
    public synchronized double getMaxError()
    {
        return mergeAndGetIfNeeded().getConfidenceFactor();
    }

    @Managed
    public double getCount()
    {
        return count.getCount();
    }

    @Managed
    public synchronized double getP50()
    {
        return convertToUnit(mergeAndGetIfNeeded().getQuantile(0.5));
    }

    @Managed
    public synchronized double getP75()
    {
        return convertToUnit(mergeAndGetIfNeeded().getQuantile(0.75));
    }

    @Managed
    public synchronized double getP90()
    {
        return convertToUnit(mergeAndGetIfNeeded().getQuantile(0.90));
    }

    @Managed
    public synchronized double getP95()
    {
        return convertToUnit(mergeAndGetIfNeeded().getQuantile(0.95));
    }

    @Managed
    public synchronized double getP99()
    {
        return convertToUnit(mergeAndGetIfNeeded().getQuantile(0.99));
    }

    @Managed
    public synchronized double getMin()
    {
        return convertToUnit(mergeAndGetIfNeeded().getMin());
    }

    @Managed
    public synchronized double getMax()
    {
        return convertToUnit(mergeAndGetIfNeeded().getMax());
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

        List<Long> values;
        synchronized (this) {
            values = mergeAndGetIfNeeded().getQuantiles(percentiles);
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

    private QuantileDigest mergeAndGetIfNeeded()
    {
        long now = System.currentTimeMillis();

        if (now - lastMerge > 1000) {
            lastMerge = now;
            for (int i = 0; i < SEGMENTS; i++) {
                synchronized (locks[i]) {
                    merged.merge(partials[i]);
                    partials[i] = new QuantileDigest(MAX_ERROR, alpha);
                }
            }
        }

        return merged;
    }

    public TimeDistributionSnapshot snapshot()
    {
        return new TimeDistributionSnapshot(
                getMaxError(),
                getCount(),
                getP50(),
                getP75(),
                getP90(),
                getP95(),
                getP99(),
                getMin(),
                getMax(),
                getUnit());
    }

    public static class TimeDistributionSnapshot
    {
        private final double maxError;
        private final double count;
        private final double p50;
        private final double p75;
        private final double p90;
        private final double p95;
        private final double p99;
        private final double min;
        private final double max;
        private final TimeUnit unit;

        @JsonCreator
        public TimeDistributionSnapshot(
                @JsonProperty("maxError") double maxError,
                @JsonProperty("count") double count,
                @JsonProperty("p50") double p50,
                @JsonProperty("p75") double p75,
                @JsonProperty("p90") double p90,
                @JsonProperty("p95") double p95,
                @JsonProperty("p99") double p99,
                @JsonProperty("min") double min,
                @JsonProperty("max") double max,
                @JsonProperty("unit") TimeUnit unit)
        {
            this.maxError = maxError;
            this.count = count;
            this.p50 = p50;
            this.p75 = p75;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        @JsonProperty
        public double getMaxError()
        {
            return maxError;
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
        public TimeUnit unit()
        {
            return unit;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("maxError", maxError)
                    .add("count", count)
                    .add("p50", p50)
                    .add("p75", p75)
                    .add("p90", p90)
                    .add("p95", p95)
                    .add("p99", p99)
                    .add("min", min)
                    .add("max", max)
                    .add("unit", unit)
                    .toString();
        }
    }
}

package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import org.weakref.jmx.Managed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Distribution
{
    private final static double MAX_ERROR = 0.01;

    private final QuantileDigest digest;

    public Distribution()
    {
        digest = new QuantileDigest(MAX_ERROR);
    }

    public Distribution(double alpha)
    {
        digest = new QuantileDigest(MAX_ERROR, alpha);
    }

    public void add(long value)
    {
        digest.add(value);
    }

    @Managed
    public double getMaxError()
    {
        return digest.getConfidenceFactor();
    }

    @Managed
    public double getCount()
    {
        return digest.getCount();
    }

    @Managed
    public long getP50()
    {
        return digest.getQuantile(0.5);
    }

    @Managed
    public long getP75()
    {
        return digest.getQuantile(0.75);
    }

    @Managed
    public long getP90()
    {
        return digest.getQuantile(0.90);
    }

    @Managed
    public long getP95()
    {
        return digest.getQuantile(0.95);
    }

    @Managed
    public long getP99()
    {
        return digest.getQuantile(0.99);
    }

    @Managed
    public long getMin()
    {
        return digest.getMin();
    }

    @Managed
    public long getMax()
    {
        return digest.getMax();
    }

    @Managed
    public Map<Double, Long> getPercentiles()
    {
        List<Double> percentiles = new ArrayList<>(100);
        for (int i = 0; i < 100; ++i) {
            percentiles.add(i / 100.0);
        }

        List<Long> values = digest.getQuantiles(percentiles);

        Map<Double, Long> result = new LinkedHashMap<>(values.size());
        for (int i = 0; i < percentiles.size(); ++i) {
            result.put(percentiles.get(i), values.get(i));
        }

        return result;
    }

    public DistributionSnapshot snapshot()
    {
        return new DistributionSnapshot(
                getMaxError(),
                getCount(),
                getP50(),
                getP75(),
                getP90(),
                getP95(),
                getP99(),
                getMin(),
                getMax());
    }

    public static class DistributionSnapshot
    {
        private final double maxError;
        private final double count;
        private final long p50;
        private final long p75;
        private final long p90;
        private final long p95;
        private final long p99;
        private final long min;
        private final long max;

        @JsonCreator
        public DistributionSnapshot(
                @JsonProperty("maxError") double maxError,
                @JsonProperty("count") double count,
                @JsonProperty("p50") long p50,
                @JsonProperty("p75") long p75,
                @JsonProperty("p90") long p90,
                @JsonProperty("p95") long p95,
                @JsonProperty("p99") long p99,
                @JsonProperty("min") long min,
                @JsonProperty("max") long max)
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
        public long getP50()
        {
            return p50;
        }

        @JsonProperty
        public long getP75()
        {
            return p75;
        }

        @JsonProperty
        public long getP90()
        {
            return p90;
        }

        @JsonProperty
        public long getP95()
        {
            return p95;
        }

        @JsonProperty
        public long getP99()
        {
            return p99;
        }

        @JsonProperty
        public long getMin()
        {
            return min;
        }

        @JsonProperty
        public long getMax()
        {
            return max;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("maxError", maxError)
                    .add("count", count)
                    .add("p50", p50)
                    .add("p75", p75)
                    .add("p90", p90)
                    .add("p95", p95)
                    .add("p99", p99)
                    .add("min", min)
                    .add("max", max)
                    .toString();
        }
    }
}

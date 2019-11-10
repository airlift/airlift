package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class Distribution
{
    @GuardedBy("this")
    private final DecayTDigest digest;

    private final DecayCounter total;

    public Distribution()
    {
        this(0);
    }

    public Distribution(double alpha)
    {
        this(new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha), new DecayCounter(alpha));
    }

    private Distribution(DecayTDigest digest, DecayCounter total)
    {
        this.digest = requireNonNull(digest, "digest is null");
        this.total = requireNonNull(total, "total is null");
    }

    public synchronized void add(long value)
    {
        digest.add(value);
        total.add(value);
    }

    public synchronized void add(long value, long count)
    {
        digest.add(value, count);
        total.add(value * count);
    }

    public synchronized Distribution duplicate()
    {
        return new Distribution(digest.duplicate(), total.duplicate());
    }

    @Managed
    public synchronized double getCount()
    {
        return digest.getCount();
    }

    @Managed
    public synchronized double getTotal()
    {
        return total.getCount();
    }

    @Managed
    public synchronized double getP01()
    {
        return digest.valueAt(0.01);
    }

    @Managed
    public synchronized double getP05()
    {
        return digest.valueAt(0.05);
    }

    @Managed
    public synchronized double getP10()
    {
        return digest.valueAt(0.10);
    }

    @Managed
    public synchronized double getP25()
    {
        return digest.valueAt(0.25);
    }

    @Managed
    public synchronized double getP50()
    {
        return digest.valueAt(0.5);
    }

    @Managed
    public synchronized double getP75()
    {
        return digest.valueAt(0.75);
    }

    @Managed
    public synchronized double getP90()
    {
        return digest.valueAt(0.90);
    }

    @Managed
    public synchronized double getP95()
    {
        return digest.valueAt(0.95);
    }

    @Managed
    public synchronized double getP99()
    {
        return digest.valueAt(0.99);
    }

    @Managed
    public synchronized double getMin()
    {
        return digest.getMin();
    }

    @Managed
    public synchronized double getMax()
    {
        return digest.getMax();
    }

    @Managed
    public synchronized double getAvg()
    {
        return getTotal() / getCount();
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
            result.put(percentiles.get(i), values.get(i));
        }

        return result;
    }

    public synchronized List<Double> getPercentiles(List<Double> percentiles)
    {
        return digest.valuesAt(percentiles);
    }

    public synchronized DistributionSnapshot snapshot()
    {
        List<Double> quantiles = digest.valuesAt(ImmutableList.of(0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99));
        return new DistributionSnapshot(
                getCount(),
                getTotal(),
                quantiles.get(0),
                quantiles.get(1),
                quantiles.get(2),
                quantiles.get(3),
                quantiles.get(4),
                quantiles.get(5),
                quantiles.get(6),
                quantiles.get(7),
                quantiles.get(8),
                getMin(),
                getMax(),
                getAvg());
    }

    public static class DistributionSnapshot
    {
        private final double count;
        private final double total;
        private final double p01;
        private final double p05;
        private final double p10;
        private final double p25;
        private final double p50;
        private final double p75;
        private final double p90;
        private final double p95;
        private final double p99;
        private final double min;
        private final double max;
        private final double avg;

        @JsonCreator
        public DistributionSnapshot(
                @JsonProperty("count") double count,
                @JsonProperty("total") double total,
                @JsonProperty("p01") double p01,
                @JsonProperty("p05") double p05,
                @JsonProperty("p10") double p10,
                @JsonProperty("p25") double p25,
                @JsonProperty("p50") double p50,
                @JsonProperty("p75") double p75,
                @JsonProperty("p90") double p90,
                @JsonProperty("p95") double p95,
                @JsonProperty("p99") double p99,
                @JsonProperty("min") double min,
                @JsonProperty("max") double max,
                @JsonProperty("avg") double avg)
        {
            this.count = count;
            this.total = total;
            this.p01 = p01;
            this.p05 = p05;
            this.p10 = p10;
            this.p25 = p25;
            this.p50 = p50;
            this.p75 = p75;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        @JsonProperty
        public double getCount()
        {
            return count;
        }

        @JsonProperty
        public double getTotal()
        {
            return total;
        }

        @JsonProperty
        public double getP01()
        {
            return p01;
        }

        @JsonProperty
        public double getP05()
        {
            return p05;
        }

        @JsonProperty
        public double getP10()
        {
            return p10;
        }

        @JsonProperty
        public double getP25()
        {
            return p25;
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

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("count", count)
                    .add("total", total)
                    .add("p01", p01)
                    .add("p05", p05)
                    .add("p10", p10)
                    .add("p25", p25)
                    .add("p50", p50)
                    .add("p75", p75)
                    .add("p90", p90)
                    .add("p95", p95)
                    .add("p99", p99)
                    .add("min", min)
                    .add("max", max)
                    .add("avg", avg)
                    .toString();
        }
    }
}

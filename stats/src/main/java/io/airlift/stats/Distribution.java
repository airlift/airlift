package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.weakref.jmx.Managed;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class Distribution
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final double alpha;
    @GuardedBy("this")
    private DecayTDigest digest;

    private final DecayCounter total;

    public Distribution()
    {
        this(0);
    }

    public Distribution(double alpha)
    {
        this(alpha, new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha), new DecayCounter(alpha));
    }

    private Distribution(double alpha, DecayTDigest digest, DecayCounter total)
    {
        this.alpha = alpha;
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
        return new Distribution(alpha, digest.duplicate(), total.duplicate());
    }

    @Managed
    public synchronized void reset()
    {
        total.reset();
        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
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
        double[] values;
        synchronized (this) {
            values = digest.valuesAt(PERCENTILES);
        }

        verify(values.length == PERCENTILES.length, "result length mismatch");

        Map<Double, Double> result = new LinkedHashMap<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            result.put(PERCENTILES[i], values[i]);
        }

        return result;
    }

    public synchronized double[] getPercentiles(double... percentiles)
    {
        return digest.valuesAt(percentiles);
    }

    public DistributionSnapshot snapshot()
    {
        double totalCount;
        double digestCount;
        double min;
        double max;
        double[] quantiles;
        synchronized (this) {
            totalCount = total.getCount();
            digestCount = digest.getCount();
            min = digest.getMin();
            max = digest.getMax();
            quantiles = digest.valuesAt(SNAPSHOT_QUANTILES);
        }
        double average = totalCount / digestCount;
        return new DistributionSnapshot(
                digestCount,
                totalCount,
                quantiles[0], // p01
                quantiles[1], // p05
                quantiles[2], // p10
                quantiles[3], // p25
                quantiles[4], // p50
                quantiles[5], // p75
                quantiles[6], // p90
                quantiles[7], // p95
                quantiles[8], // p99
                min,
                max,
                average);
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

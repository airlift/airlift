package io.airlift.stats;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.weakref.jmx.Managed;

@ThreadSafe
public class Distribution {
    private static final double[] SNAPSHOT_QUANTILES =
            new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
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

    public Distribution() {
        this(0);
    }

    public Distribution(double alpha) {
        this(alpha, new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha), new DecayCounter(alpha));
    }

    private Distribution(double alpha, DecayTDigest digest, DecayCounter total) {
        this.alpha = alpha;
        this.digest = requireNonNull(digest, "digest is null");
        this.total = requireNonNull(total, "total is null");
    }

    public synchronized void add(long value) {
        digest.add(value);
        total.add(value);
    }

    public synchronized void add(long value, long count) {
        digest.add(value, count);
        total.add(value * count);
    }

    public synchronized Distribution duplicate() {
        return new Distribution(alpha, digest.duplicate(), total.duplicate());
    }

    @Managed
    public synchronized void reset() {
        total.reset();
        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
    }

    @Managed
    public synchronized double getCount() {
        return digest.getCount();
    }

    @Managed
    public synchronized double getTotal() {
        return total.getCount();
    }

    @Managed
    public synchronized double getP01() {
        return digest.valueAt(0.01);
    }

    @Managed
    public synchronized double getP05() {
        return digest.valueAt(0.05);
    }

    @Managed
    public synchronized double getP10() {
        return digest.valueAt(0.10);
    }

    @Managed
    public synchronized double getP25() {
        return digest.valueAt(0.25);
    }

    @Managed
    public synchronized double getP50() {
        return digest.valueAt(0.5);
    }

    @Managed
    public synchronized double getP75() {
        return digest.valueAt(0.75);
    }

    @Managed
    public synchronized double getP90() {
        return digest.valueAt(0.90);
    }

    @Managed
    public synchronized double getP95() {
        return digest.valueAt(0.95);
    }

    @Managed
    public synchronized double getP99() {
        return digest.valueAt(0.99);
    }

    @Managed
    public synchronized double getMin() {
        return digest.getMin();
    }

    @Managed
    public synchronized double getMax() {
        return digest.getMax();
    }

    @Managed
    public synchronized double getAvg() {
        return getTotal() / getCount();
    }

    @Managed
    public Map<Double, Double> getPercentiles() {
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

    public DistributionSnapshot snapshot() {
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

    public record DistributionSnapshot(
            double count,
            double total,
            double p01,
            double p05,
            double p10,
            double p25,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double min,
            double max,
            double avg) {}
}

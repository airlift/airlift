package io.airlift.stats;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.weakref.jmx.Managed;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Verify.verify;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class Distribution
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;
    private static final int STRIPES = clamp(Runtime.getRuntime().availableProcessors(), 2, 16);

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final double alpha;
    private final Object[] locks = new Object[STRIPES];
    // @GuardedBy("locks[i]") for partials[i]
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    @GuardedBy("this")
    private DecayTDigest merged;

    private final DecayCounter total;
    private final DecayCounter partialTotal;

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
        this.merged = requireNonNull(digest, "digest is null");
        this.total = requireNonNull(total, "total is null");
        this.partialTotal = new DecayCounter(alpha);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    public void add(long value)
    {
        add(value, 1);
    }

    public void add(long value, long count)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            if (partials[segment] == null) {
                partials[segment] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
            }
            partials[segment].add(value, count);
        }
        partialTotal.add(value * count); // Fine outside of lock as DecayCounter is thread safe
    }

    public synchronized Distribution duplicate()
    {
        mergePartials();
        return new Distribution(alpha, merged.duplicate(), total.duplicate());
    }

    @Managed
    public synchronized void reset()
    {
        total.reset();
        partialTotal.reset();
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
            }
        }
    }

    @Managed
    public synchronized double getCount()
    {
        mergePartials();
        return merged.getCount();
    }

    @Managed
    public synchronized double getTotal()
    {
        mergePartials();
        return total.getCount();
    }

    @Managed
    public synchronized double getP01()
    {
        mergePartials();
        return merged.valueAt(0.01);
    }

    @Managed
    public synchronized double getP05()
    {
        mergePartials();
        return merged.valueAt(0.05);
    }

    @Managed
    public synchronized double getP10()
    {
        mergePartials();
        return merged.valueAt(0.10);
    }

    @Managed
    public synchronized double getP25()
    {
        mergePartials();
        return merged.valueAt(0.25);
    }

    @Managed
    public synchronized double getP50()
    {
        mergePartials();
        return merged.valueAt(0.5);
    }

    @Managed
    public synchronized double getP75()
    {
        mergePartials();
        return merged.valueAt(0.75);
    }

    @Managed
    public synchronized double getP90()
    {
        mergePartials();
        return merged.valueAt(0.90);
    }

    @Managed
    public synchronized double getP95()
    {
        mergePartials();
        return merged.valueAt(0.95);
    }

    @Managed
    public synchronized double getP99()
    {
        mergePartials();
        return merged.valueAt(0.99);
    }

    @Managed
    public synchronized double getMin()
    {
        mergePartials();
        return merged.getMin();
    }

    @Managed
    public synchronized double getMax()
    {
        mergePartials();
        return merged.getMax();
    }

    @Managed
    public synchronized double getAvg()
    {
        mergePartials();
        return total.getCount() / merged.getCount();
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        double[] values;
        synchronized (this) {
            mergePartials();
            values = merged.valuesAt(PERCENTILES);
        }

        verify(values.length == PERCENTILES.length, "result length mismatch");

        Map<Double, Double> result = new LinkedHashMap<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            result.put(PERCENTILES[i], values[i]);
        }

        return result;
    }

    public DistributionSnapshot snapshot()
    {
        double totalCount;
        double digestCount;
        double min;
        double max;
        double[] quantiles;
        synchronized (this) {
            mergePartials();
            totalCount = total.getCount();
            digestCount = merged.getCount();
            min = merged.getMin();
            max = merged.getMax();
            quantiles = merged.valuesAt(SNAPSHOT_QUANTILES);
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

    // Unlike TimeDistribution, partials are folded in on every read rather than behind a
    // time threshold: readers expect to see their own writes immediately (e.g. CachedDistribution
    // in http-client builds a Distribution and reads it right away). The sweep is cheap when
    // no values were added since the last read.
    @GuardedBy("this")
    private void mergePartials()
    {
        for (int i = 0; i < STRIPES; i++) {
            synchronized (locks[i]) {
                if (partials[i] == null) {
                    continue;
                }
                merged.merge(partials[i]);
                // Reset the partial
                partials[i] = null;
            }
        }
        total.merge(partialTotal);
        partialTotal.reset();
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

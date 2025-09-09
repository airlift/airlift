package io.airlift.stats;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.weakref.jmx.Managed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Verify.verify;
import static java.lang.Math.floorMod;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    private static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;
    private static final int STRIPES = 16;

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final double alpha;
    private final Object[] locks = new Object[STRIPES];
    @GuardedBy("locks")
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    @GuardedBy("this")
    private DecayTDigest merged;
    @GuardedBy("this")
    private long lastMerge;
    private final DecayCounter total;
    private final TimeUnit unit;

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
        requireNonNull(unit, "unit is null");
        this.alpha = alpha;
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
            partials[i] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        }
        total = new DecayCounter(alpha);
        this.unit = unit;
    }

    public void add(long value)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            partials[segment].add(value);
        }
        total.add(value);
    }

    @Managed
    public double getCount()
    {
        return mergeAndGetIfNeeded().getCount();
    }

    @Managed
    public double getP50()
    {
        return convertToUnit(mergeAndGetIfNeeded().valueAt(0.5));
    }

    @Managed
    public double getP75()
    {
        return convertToUnit(mergeAndGetIfNeeded().valueAt(0.75));
    }

    @Managed
    public double getP90()
    {
        return convertToUnit(mergeAndGetIfNeeded().valueAt(0.90));
    }

    @Managed
    public double getP95()
    {
        return convertToUnit(mergeAndGetIfNeeded().valueAt(0.95));
    }

    @Managed
    public double getP99()
    {
        return convertToUnit(mergeAndGetIfNeeded().valueAt(0.99));
    }

    @Managed
    public double getMin()
    {
        return convertToUnit(mergeAndGetIfNeeded().getMin());
    }

    @Managed
    public double getMax()
    {
        return convertToUnit(mergeAndGetIfNeeded().getMax());
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
        double[] values = mergeAndGetIfNeeded(true)
                .valuesAt(PERCENTILES);

        verify(values.length == PERCENTILES.length, "values length mismatch");

        Map<Double, Double> result = new LinkedHashMap<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            result.put(PERCENTILES[i], values[i]);
        }

        return result;
    }

    private DecayTDigest mergeAndGetIfNeeded()
    {
        return mergeAndGetIfNeeded(false);
    }

    private DecayTDigest mergeAndGetIfNeeded(boolean forceMerge)
    {
        synchronized (this) {
            if (forceMerge || System.nanoTime() - lastMerge > MERGE_THRESHOLD_NANOS) {
                merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
                for (int i = 0; i < STRIPES; i++) {
                    synchronized (locks[i]) {
                        merged.merge(partials[i]);
                        // Reset the partial
                        partials[i] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
                    }
                }
                lastMerge = System.nanoTime();
            }
        }

        return merged;
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
        return convertToUnit(nanos, (double) unit.toNanos(1));
    }

    private static double convertToUnit(double nanos, double unitNanos)
    {
        return nanos / unitNanos;
    }

    public TimeDistributionSnapshot snapshot()
    {
        double totalCount;
        double digestCount;
        double min;
        double max;
        double[] quantiles;
        synchronized (this) {
            DecayTDigest digest = mergeAndGetIfNeeded(true);
            totalCount = total.getCount();
            digestCount = digest.getCount();
            min = digest.getMin();
            max = digest.getMax();
            quantiles = digest.valuesAt(SNAPSHOT_QUANTILES);
        }
        double unitNanos = (double) unit.toNanos(1);
        double average = convertToUnit(totalCount, unitNanos) / digestCount;
        return new TimeDistributionSnapshot(
                digestCount,
                convertToUnit(quantiles[0], unitNanos), // p50
                convertToUnit(quantiles[1], unitNanos), // p75
                convertToUnit(quantiles[2], unitNanos), // p90
                convertToUnit(quantiles[3], unitNanos), // p95
                convertToUnit(quantiles[4], unitNanos), // p99
                convertToUnit(min, unitNanos),
                convertToUnit(max, unitNanos),
                average,
                unit);
    }

    @Managed
    public synchronized void reset()
    {
        total.reset();
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
            }
        }
    }

    public record TimeDistributionSnapshot(
            double count,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double min,
            double max,
            double avg,
            TimeUnit unit)
    {
        public TimeDistributionSnapshot
        {
            requireNonNull(unit, "unit is null");
        }
    }
}

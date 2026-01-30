package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.weakref.jmx.Managed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Ticker.systemTicker;
import static com.google.common.base.Verify.verify;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    @VisibleForTesting
    static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;
    private static final int STRIPES = clamp(Runtime.getRuntime().availableProcessors(), 2, 16);

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final Ticker ticker;
    private final double alpha;
    private final Object[] locks = new Object[STRIPES];
    // @GuardedBy("locks[i]") for partials[i]
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    @GuardedBy("this")
    private DecayTDigest merged;
    @GuardedBy("this")
    private long lastMerge;
    private final DecayCounter total;
    private final DecayCounter partialTotal;
    private final TimeUnit unit;

    public TimeDistribution()
    {
        this(SECONDS);
    }

    public TimeDistribution(TimeUnit unit)
    {
        this(systemTicker(), 0, unit);
    }

    public TimeDistribution(Ticker ticker)
    {
        this(ticker, 0, SECONDS);
    }

    public TimeDistribution(double alpha)
    {
        this(systemTicker(), alpha, SECONDS);
    }

    public TimeDistribution(Ticker ticker, double alpha, TimeUnit unit)
    {
        requireNonNull(ticker, "ticker is null");
        requireNonNull(unit, "unit is null");
        this.alpha = alpha;
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
        total = new DecayCounter(alpha);
        partialTotal = new DecayCounter(alpha);
        this.ticker = ticker;
        this.unit = unit;
        this.lastMerge = ticker.read(); // do not merge immediately
    }

    public void add(long value)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            if (partials[segment] == null) {
                partials[segment] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
            }
            partials[segment].add(value);
        }
        partialTotal.add(value); // Fine outside of lock as DecayCounter is thread safe
    }

    @Managed
    public double getCount()
    {
        synchronized (this) {
            mergeIfNeeded();
            return merged.getCount();
        }
    }

    @Managed
    public double getP50()
    {
        double p50;
        synchronized (this) {
            mergeIfNeeded();
            p50 = merged.valueAt(0.5);
        }
        return convertToUnit(p50);
    }

    @Managed
    public double getP75()
    {
        double p75;
        synchronized (this) {
            mergeIfNeeded();
            p75 = merged.valueAt(0.75);
        }
        return convertToUnit(p75);
    }

    @Managed
    public double getP90()
    {
        double p90;
        synchronized (this) {
            mergeIfNeeded();
            p90 = merged.valueAt(0.90);
        }
        return convertToUnit(p90);
    }

    @Managed
    public double getP95()
    {
        double p95;
        synchronized (this) {
            mergeIfNeeded();
            p95 = merged.valueAt(0.95);
        }
        return convertToUnit(p95);
    }

    @Managed
    public double getP99()
    {
        double p99;
        synchronized (this) {
            mergeIfNeeded();
            p99 = merged.valueAt(0.99);
        }
        return convertToUnit(p99);
    }

    @Managed
    public double getMin()
    {
        double min;
        synchronized (this) {
            mergeIfNeeded();
            min = merged.getMin();
        }
        return convertToUnit(min);
    }

    @Managed
    public double getMax()
    {
        double max;
        synchronized (this) {
            mergeIfNeeded();
            max = merged.getMax();
        }
        return convertToUnit(max);
    }

    @Managed
    public double getAvg()
    {
        double digestCount;
        double totalCount;
        synchronized (this) {
            mergeIfNeeded();
            digestCount = merged.getCount();
            totalCount = total.getCount();
        }
        return convertToUnit(totalCount) / digestCount;
    }

    @Managed
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        double[] values;
        synchronized (this) {
            mergeIfNeeded(true);
            values = merged.valuesAt(PERCENTILES);
        }

        verify(values.length == PERCENTILES.length, "values length mismatch");

        Map<Double, Double> result = new LinkedHashMap<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            result.put(PERCENTILES[i], values[i]);
        }

        return result;
    }

    private void mergeIfNeeded()
    {
        mergeIfNeeded(false);
    }

    private void mergeIfNeeded(boolean forceMerge)
    {
        synchronized (this) {
            if (forceMerge || ticker.read() - lastMerge >= MERGE_THRESHOLD_NANOS) {
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
                lastMerge = ticker.read();
            }
        }
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
            mergeIfNeeded(true);
            DecayTDigest digest = merged;
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
        partialTotal.reset();
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
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

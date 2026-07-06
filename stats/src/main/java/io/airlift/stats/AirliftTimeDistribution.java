package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Verify.verify;
import static io.airlift.stats.Percentiles.PERCENTILES;
import static io.airlift.stats.Percentiles.toMap;
import static io.airlift.stats.TimeDistributionImplementation.convertToUnit;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;

final class AirliftTimeDistribution
        implements TimeDistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.5, 0.75, 0.9, 0.95, 0.99};
    private static final int STRIPES = clamp(Runtime.getRuntime().availableProcessors(), 2, 16);

    private final Ticker ticker;
    // immutable config shared by every sub-structure; null when this distribution does not decay
    @Nullable
    private final DecayConfig config;
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

    AirliftTimeDistribution(Ticker ticker, @Nullable DecayConfig config, TimeUnit unit)
    {
        // the config is immutable and shared; each sub-structure derives its own decay state
        this.config = config;
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
        total = new DecayCounter(config);
        partialTotal = new DecayCounter(config);
        this.ticker = ticker;
        this.unit = unit;
        this.lastMerge = ticker.read(); // do not merge immediately
    }

    @Override
    public void add(long value)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            if (partials[segment] == null) {
                partials[segment] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
            }
            partials[segment].add(value);
        }
        partialTotal.add(value); // Fine outside of lock as DecayCounter is thread safe
    }

    @Override
    public double getCount()
    {
        synchronized (this) {
            mergeIfNeeded();
            return merged.getCount();
        }
    }

    @Override
    public double getP50()
    {
        double p50;
        synchronized (this) {
            mergeIfNeeded();
            p50 = merged.valueAt(0.5);
        }
        return convertToUnit(p50, unit);
    }

    @Override
    public double getP75()
    {
        double p75;
        synchronized (this) {
            mergeIfNeeded();
            p75 = merged.valueAt(0.75);
        }
        return convertToUnit(p75, unit);
    }

    @Override
    public double getP90()
    {
        double p90;
        synchronized (this) {
            mergeIfNeeded();
            p90 = merged.valueAt(0.90);
        }
        return convertToUnit(p90, unit);
    }

    @Override
    public double getP95()
    {
        double p95;
        synchronized (this) {
            mergeIfNeeded();
            p95 = merged.valueAt(0.95);
        }
        return convertToUnit(p95, unit);
    }

    @Override
    public double getP99()
    {
        double p99;
        synchronized (this) {
            mergeIfNeeded();
            p99 = merged.valueAt(0.99);
        }
        return convertToUnit(p99, unit);
    }

    @Override
    public double getMin()
    {
        double min;
        synchronized (this) {
            mergeIfNeeded();
            min = merged.getMin();
        }
        return convertToUnit(min, unit);
    }

    @Override
    public double getMax()
    {
        double max;
        synchronized (this) {
            mergeIfNeeded();
            max = merged.getMax();
        }
        return convertToUnit(max, unit);
    }

    @Override
    public double getAvg()
    {
        double digestCount;
        double totalCount;
        synchronized (this) {
            mergeIfNeeded();
            digestCount = merged.getCount();
            totalCount = total.getCount();
        }
        return convertToUnit(totalCount, unit) / digestCount;
    }

    @Override
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Override
    public Map<Double, Double> getPercentiles()
    {
        double[] values;
        synchronized (this) {
            mergeIfNeeded(true);
            values = merged.valuesAt(PERCENTILES);
        }

        verify(values.length == PERCENTILES.length, "values length mismatch");

        return toMap(values);
    }

    private void mergeIfNeeded()
    {
        mergeIfNeeded(false);
    }

    private void mergeIfNeeded(boolean forceMerge)
    {
        synchronized (this) {
            if (forceMerge || ticker.read() - lastMerge >= TimeDistribution.MERGE_THRESHOLD_NANOS) {
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

    @Override
    public TimeDistribution.TimeDistributionSnapshot snapshot()
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
        double average = convertToUnit(totalCount, unit) / digestCount;
        return new TimeDistribution.TimeDistributionSnapshot(
                digestCount,
                convertToUnit(quantiles[0], unit), // p50
                convertToUnit(quantiles[1], unit), // p75
                convertToUnit(quantiles[2], unit), // p90
                convertToUnit(quantiles[3], unit), // p95
                convertToUnit(quantiles[4], unit), // p99
                convertToUnit(min, unit),
                convertToUnit(max, unit),
                average,
                unit);
    }

    @Override
    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return Optional.empty();
    }

    @Override
    public synchronized void reset()
    {
        total.reset();
        partialTotal.reset();
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
            }
        }
    }
}

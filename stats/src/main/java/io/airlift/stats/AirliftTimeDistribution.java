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
import static java.lang.Math.max;

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
    // @GuardedBy("locks[i]") for partials[i] and partialTotals[i]; the total is kept with its digest so a merge moves both atomically
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    private final DecayCounter[] partialTotals = new DecayCounter[STRIPES];
    // @GuardedBy("locks[i]") the second the last update to stripe i was weighted at; only
    // meaningful while partials[i] is non-null, and seeded when the partial is created
    private final long[] partialSeconds = new long[STRIPES];
    @GuardedBy("this")
    private DecayTDigest merged;
    @GuardedBy("this")
    private long lastMerge;
    private final DecayCounter total;
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
        this.ticker = ticker;
        this.unit = unit;
        this.lastMerge = ticker.read(); // do not merge immediately
    }

    @Override
    public long nowInSeconds()
    {
        return config == null ? 0 : config.nowInSeconds();
    }

    @Override
    public void add(long value)
    {
        // the digest and the total share the config (and so the ticker), so read the clock once
        // per add rather than once per structure
        add(value, nowInSeconds());
    }

    @Override
    public void add(long value, long nowInSeconds)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            long second;
            if (partials[segment] == null) {
                // Anchor at the caller's second, since a new clock read could split this update across seconds.
                // The ticker may be negative, so the stripe's second restarts here instead of at zero.
                partials[segment] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config, nowInSeconds);
                partialTotals[segment] = new DecayCounter(config, nowInSeconds);
                second = nowInSeconds;
            }
            else {
                // The caller read the clock before waiting for the lock, so keep the stripe's second monotonic.
                second = max(nowInSeconds, partialSeconds[segment]);
            }
            // written only when the second changes: the array is shared by every stripe, so a write
            // on every add would bounce its cache line between threads
            if (second != partialSeconds[segment]) {
                partialSeconds[segment] = second;
            }
            partials[segment].add(value, 1, second);
            partialTotals[segment].add(value, second);
        }
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
                        total.merge(partialTotals[i]);
                        // Reset the partial
                        partials[i] = null;
                        partialTotals[i] = null;
                    }
                }
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
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
                partialTotals[i] = null;
            }
        }
    }
}

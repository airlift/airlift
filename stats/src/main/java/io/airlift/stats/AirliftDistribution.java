package io.airlift.stats;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Verify.verify;
import static io.airlift.stats.Percentiles.PERCENTILES;
import static io.airlift.stats.Percentiles.toMap;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

final class AirliftDistribution
        implements DistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};

    // immutable config shared by every sub-structure; null when this distribution does not decay
    @Nullable
    private final DecayConfig config;
    private static final int STRIPES = clamp(Runtime.getRuntime().availableProcessors(), 2, 16);

    private final Object[] locks = new Object[STRIPES];
    // @GuardedBy("locks[i]") for partials[i] and partialTotals[i]; the total is kept with its digest so a merge moves both atomically
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    private final DecayCounter[] partialTotals = new DecayCounter[STRIPES];
    // @GuardedBy("locks[i]") the second the last update to stripe i was weighted at; only
    // meaningful while partials[i] is non-null, and seeded when the partial is created
    private final long[] partialSeconds = new long[STRIPES];
    @GuardedBy("this")
    private DecayTDigest digest;
    @GuardedBy("this")
    private final DecayCounter total;

    AirliftDistribution(@Nullable DecayConfig config)
    {
        this(config, new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config), new DecayCounter(config));
    }

    private AirliftDistribution(@Nullable DecayConfig config, DecayTDigest digest, DecayCounter total)
    {
        this.config = config;
        this.digest = requireNonNull(digest, "digest is null");
        this.total = requireNonNull(total, "total is null");
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void add(long value)
    {
        add(value, 1);
    }

    @Override
    public long nowInSeconds()
    {
        return config == null ? 0 : config.nowInSeconds();
    }

    @Override
    public void add(long value, long count)
    {
        // the digest and the total share the config (and so the ticker), so read the clock once
        // per add rather than once per structure
        add(value, count, nowInSeconds());
    }

    @Override
    public void add(long value, long count, long nowInSeconds)
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
            partials[segment].add(value, count, second);
            partialTotals[segment].add(value * count, second);
        }
    }

    @GuardedBy("this")
    private void mergePartials()
    {
        for (int i = 0; i < STRIPES; i++) {
            synchronized (locks[i]) {
                if (partials[i] == null) {
                    continue;
                }
                // digest and total are folded under the same stripe lock as add(), so no update is lost
                digest.merge(partials[i]);
                total.merge(partialTotals[i]);
                partials[i] = null;
                partialTotals[i] = null;
            }
        }
    }

    @Override
    public synchronized DistributionImplementation duplicate()
    {
        mergePartials();
        // the config is immutable and freely shared; digest/total keep their own landmark-preserving copies
        return new AirliftDistribution(config, digest.duplicate(), total.duplicate());
    }

    @Override
    public synchronized void reset()
    {
        total.reset();
        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
        for (int i = 0; i < STRIPES; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
                partialTotals[i] = null;
            }
        }
    }

    @Override
    public synchronized double getCount()
    {
        mergePartials();
        return digest.getCount();
    }

    @Override
    public synchronized double getTotal()
    {
        mergePartials();
        return total.getCount();
    }

    @Override
    public synchronized double getP01()
    {
        mergePartials();
        return digest.valueAt(0.01);
    }

    @Override
    public synchronized double getP05()
    {
        mergePartials();
        return digest.valueAt(0.05);
    }

    @Override
    public synchronized double getP10()
    {
        mergePartials();
        return digest.valueAt(0.10);
    }

    @Override
    public synchronized double getP25()
    {
        mergePartials();
        return digest.valueAt(0.25);
    }

    @Override
    public synchronized double getP50()
    {
        mergePartials();
        return digest.valueAt(0.5);
    }

    @Override
    public synchronized double getP75()
    {
        mergePartials();
        return digest.valueAt(0.75);
    }

    @Override
    public synchronized double getP90()
    {
        mergePartials();
        return digest.valueAt(0.90);
    }

    @Override
    public synchronized double getP95()
    {
        mergePartials();
        return digest.valueAt(0.95);
    }

    @Override
    public synchronized double getP99()
    {
        mergePartials();
        return digest.valueAt(0.99);
    }

    @Override
    public synchronized double getMin()
    {
        mergePartials();
        return digest.getMin();
    }

    @Override
    public synchronized double getMax()
    {
        mergePartials();
        return digest.getMax();
    }

    @Override
    public synchronized double getAvg()
    {
        mergePartials();
        return total.getCount() / digest.getCount();
    }

    @Override
    public Map<Double, Double> getPercentiles()
    {
        double[] values;
        synchronized (this) {
            mergePartials();
            values = digest.valuesAt(PERCENTILES);
        }

        verify(values.length == PERCENTILES.length, "result length mismatch");

        return toMap(values);
    }

    @Override
    public Distribution.DistributionSnapshot snapshot()
    {
        double totalCount;
        double digestCount;
        double min;
        double max;
        double[] quantiles;
        synchronized (this) {
            mergePartials();
            totalCount = total.getCount();
            digestCount = digest.getCount();
            min = digest.getMin();
            max = digest.getMax();
            quantiles = digest.valuesAt(SNAPSHOT_QUANTILES);
        }
        double average = totalCount / digestCount;
        return new Distribution.DistributionSnapshot(
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

    @Override
    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return Optional.empty();
    }
}

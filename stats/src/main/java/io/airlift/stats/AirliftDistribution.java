package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Verify.verify;
import static io.airlift.stats.Percentiles.PERCENTILES;
import static io.airlift.stats.Percentiles.toMap;
import static java.util.Objects.requireNonNull;

final class AirliftDistribution
        implements DistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};

    // immutable config shared by every sub-structure; null when this distribution does not decay
    @Nullable
    private final DecayConfig config;
    private DecayTDigest digest;

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
    }

    @Override
    public synchronized void add(long value)
    {
        digest.add(value);
        total.add(value);
    }

    @Override
    public synchronized void add(long value, long count)
    {
        digest.add(value, count);
        total.add(value * count);
    }

    @Override
    public synchronized DistributionImplementation duplicate()
    {
        // the config is immutable and freely shared; digest/total keep their own landmark-preserving copies
        return new AirliftDistribution(config, digest.duplicate(), total.duplicate());
    }

    @Override
    public synchronized void reset()
    {
        total.reset();
        digest = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, config);
    }

    @Override
    public synchronized double getCount()
    {
        return digest.getCount();
    }

    @Override
    public synchronized double getTotal()
    {
        return total.getCount();
    }

    @Override
    public synchronized double getP01()
    {
        return digest.valueAt(0.01);
    }

    @Override
    public synchronized double getP05()
    {
        return digest.valueAt(0.05);
    }

    @Override
    public synchronized double getP10()
    {
        return digest.valueAt(0.10);
    }

    @Override
    public synchronized double getP25()
    {
        return digest.valueAt(0.25);
    }

    @Override
    public synchronized double getP50()
    {
        return digest.valueAt(0.5);
    }

    @Override
    public synchronized double getP75()
    {
        return digest.valueAt(0.75);
    }

    @Override
    public synchronized double getP90()
    {
        return digest.valueAt(0.90);
    }

    @Override
    public synchronized double getP95()
    {
        return digest.valueAt(0.95);
    }

    @Override
    public synchronized double getP99()
    {
        return digest.valueAt(0.99);
    }

    @Override
    public synchronized double getMin()
    {
        return digest.getMin();
    }

    @Override
    public synchronized double getMax()
    {
        return digest.getMax();
    }

    @Override
    public synchronized double getAvg()
    {
        return getTotal() / getCount();
    }

    @Override
    public Map<Double, Double> getPercentiles()
    {
        double[] values;
        synchronized (this) {
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

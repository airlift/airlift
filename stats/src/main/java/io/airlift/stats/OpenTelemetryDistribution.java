package io.airlift.stats;

import com.google.common.base.Ticker;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Ticker.systemTicker;
import static io.airlift.stats.DistributionImplementation.average;
import static io.airlift.stats.Percentiles.PERCENTILES;
import static io.airlift.stats.Percentiles.toMap;

final class OpenTelemetryDistribution
        implements DistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};

    private final StripedExponentialHistogram histogram;
    private final CachingHistogramSnapshot snapshotCache;

    OpenTelemetryDistribution(Ticker ticker)
    {
        histogram = new StripedExponentialHistogram();
        snapshotCache = new CachingHistogramSnapshot(histogram, ticker, Distribution.MERGE_THRESHOLD_NANOS);
    }

    private OpenTelemetryDistribution(ExponentialHistogramSnapshot snapshot)
    {
        histogram = new StripedExponentialHistogram(snapshot, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        snapshotCache = new CachingHistogramSnapshot(histogram, systemTicker(), Distribution.MERGE_THRESHOLD_NANOS);
    }

    @Override
    public void add(long value)
    {
        histogram.record(value);
    }

    @Override
    public void add(long value, long count)
    {
        histogram.record(value, count);
    }

    @Override
    public DistributionImplementation duplicate()
    {
        return new OpenTelemetryDistribution(histogram.snapshot());
    }

    @Override
    public void reset()
    {
        snapshotCache.reset();
    }

    @Override
    public double getCount()
    {
        return snapshotIfNeeded().count();
    }

    @Override
    public double getTotal()
    {
        return snapshotIfNeeded().sum();
    }

    @Override
    public double getP01()
    {
        return valueAt(0.01);
    }

    @Override
    public double getP05()
    {
        return valueAt(0.05);
    }

    @Override
    public double getP10()
    {
        return valueAt(0.10);
    }

    @Override
    public double getP25()
    {
        return valueAt(0.25);
    }

    @Override
    public double getP50()
    {
        return valueAt(0.50);
    }

    @Override
    public double getP75()
    {
        return valueAt(0.75);
    }

    @Override
    public double getP90()
    {
        return valueAt(0.90);
    }

    @Override
    public double getP95()
    {
        return valueAt(0.95);
    }

    @Override
    public double getP99()
    {
        return valueAt(0.99);
    }

    @Override
    public double getMin()
    {
        return snapshotIfNeeded().min();
    }

    @Override
    public double getMax()
    {
        return snapshotIfNeeded().max();
    }

    @Override
    public double getAvg()
    {
        ExponentialHistogramSnapshot snapshot = snapshotIfNeeded();
        return average(snapshot.sum(), snapshot.count());
    }

    @Override
    public Map<Double, Double> getPercentiles()
    {
        double[] values = ExponentialHistogram.valuesAt(snapshot(true), PERCENTILES);
        return toMap(values);
    }

    @Override
    public Distribution.DistributionSnapshot snapshot()
    {
        ExponentialHistogramSnapshot snapshot = snapshot(true);
        double[] quantiles = ExponentialHistogram.valuesAt(snapshot, SNAPSHOT_QUANTILES);
        return new Distribution.DistributionSnapshot(
                snapshot.count(),
                snapshot.sum(),
                quantiles[0], // p01
                quantiles[1], // p05
                quantiles[2], // p10
                quantiles[3], // p25
                quantiles[4], // p50
                quantiles[5], // p75
                quantiles[6], // p90
                quantiles[7], // p95
                quantiles[8], // p99
                snapshot.min(),
                snapshot.max(),
                average(snapshot.sum(), snapshot.count()));
    }

    @Override
    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return Optional.of(snapshot(true));
    }

    private double valueAt(double percentile)
    {
        return ExponentialHistogram.valuesAt(snapshotIfNeeded(), new double[] {percentile})[0];
    }

    private ExponentialHistogramSnapshot snapshotIfNeeded()
    {
        return snapshot(false);
    }

    private ExponentialHistogramSnapshot snapshot(boolean forceSnapshot)
    {
        return snapshotCache.snapshot(forceSnapshot);
    }
}

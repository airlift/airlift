package io.airlift.stats;

import com.google.common.base.Ticker;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.airlift.stats.DistributionImplementation.average;
import static io.airlift.stats.Percentiles.PERCENTILES;
import static io.airlift.stats.Percentiles.toMap;
import static io.airlift.stats.TimeDistributionImplementation.convertToUnit;

final class OpenTelemetryTimeDistribution
        implements TimeDistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.5, 0.75, 0.9, 0.95, 0.99};

    private final StripedExponentialHistogram histogram = new StripedExponentialHistogram();
    private final TimeUnit unit;
    private final CachingHistogramSnapshot snapshotCache;

    OpenTelemetryTimeDistribution(Ticker ticker, TimeUnit unit)
    {
        this.unit = unit;
        snapshotCache = new CachingHistogramSnapshot(histogram, ticker, TimeDistribution.MERGE_THRESHOLD_NANOS);
    }

    @Override
    public void add(long value)
    {
        histogram.record(value);
    }

    @Override
    public double getCount()
    {
        return snapshotIfNeeded().count();
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
        return convertToUnit(snapshotIfNeeded().min(), unit);
    }

    @Override
    public double getMax()
    {
        return convertToUnit(snapshotIfNeeded().max(), unit);
    }

    @Override
    public double getAvg()
    {
        ExponentialHistogramSnapshot snapshot = snapshotIfNeeded();
        return average(convertToUnit(snapshot.sum(), unit), snapshot.count());
    }

    @Override
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Override
    public Map<Double, Double> getPercentiles()
    {
        ExponentialHistogramSnapshot snapshot = snapshot(true);
        double[] values = ExponentialHistogram.valuesAt(snapshot, PERCENTILES);
        return toMap(values);
    }

    @Override
    public TimeDistribution.TimeDistributionSnapshot snapshot()
    {
        ExponentialHistogramSnapshot snapshot = snapshot(true);
        double[] quantiles = ExponentialHistogram.valuesAt(snapshot, SNAPSHOT_QUANTILES);
        return new TimeDistribution.TimeDistributionSnapshot(
                snapshot.count(),
                convertToUnit(quantiles[0], unit), // p50
                convertToUnit(quantiles[1], unit), // p75
                convertToUnit(quantiles[2], unit), // p90
                convertToUnit(quantiles[3], unit), // p95
                convertToUnit(quantiles[4], unit), // p99
                convertToUnit(snapshot.min(), unit),
                convertToUnit(snapshot.max(), unit),
                average(convertToUnit(snapshot.sum(), unit), snapshot.count()),
                unit);
    }

    @Override
    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return Optional.of(snapshot(true));
    }

    @Override
    public void reset()
    {
        snapshotCache.reset();
    }

    private double valueAt(double percentile)
    {
        return convertToUnit(ExponentialHistogram.valuesAt(snapshotIfNeeded(), new double[] {percentile})[0], unit);
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

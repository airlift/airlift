package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryTimeDistribution
        implements TimeDistributionImplementation
{
    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final StripedExponentialHistogram histogram = new StripedExponentialHistogram();
    private final Ticker ticker;
    private final TimeUnit unit;
    @GuardedBy("this")
    private ExponentialHistogramSnapshot cachedSnapshot;
    @GuardedBy("this")
    private long lastSnapshot;

    OpenTelemetryTimeDistribution(Ticker ticker, TimeUnit unit)
    {
        this.ticker = ticker;
        this.unit = unit;
        lastSnapshot = ticker.read(); // do not snapshot immediately
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
        if (snapshot.count() == 0) {
            return Double.NaN;
        }
        return convertToUnit(snapshot.sum(), unit) / snapshot.count();
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
        Map<Double, Double> result = new LinkedHashMap<>(PERCENTILES.length);
        for (int i = 0; i < PERCENTILES.length; i++) {
            result.put(PERCENTILES[i], values[i]);
        }
        return result;
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
                snapshot.count() == 0 ? Double.NaN : convertToUnit(snapshot.sum(), unit) / snapshot.count(),
                unit);
    }

    @Override
    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return Optional.of(snapshot(true));
    }

    @Override
    public synchronized void reset()
    {
        histogram.reset();
        cachedSnapshot = null;
        lastSnapshot = ticker.read();
    }

    private double valueAt(double percentile)
    {
        return convertToUnit(ExponentialHistogram.valuesAt(snapshotIfNeeded(), new double[] {percentile})[0], unit);
    }

    private ExponentialHistogramSnapshot snapshotIfNeeded()
    {
        return snapshot(false);
    }

    private synchronized ExponentialHistogramSnapshot snapshot(boolean forceSnapshot)
    {
        if (forceSnapshot || cachedSnapshot == null || ticker.read() - lastSnapshot >= TimeDistribution.MERGE_THRESHOLD_NANOS) {
            cachedSnapshot = histogram.snapshot();
            lastSnapshot = ticker.read();
        }
        return cachedSnapshot;
    }

    private static double convertToUnit(double nanos, TimeUnit unit)
    {
        return nanos / unit.toNanos(1);
    }
}

package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import static java.util.Objects.requireNonNull;

final class CachingHistogramSnapshot
{
    private final StripedExponentialHistogram histogram;
    private final Ticker ticker;
    private final long snapshotThresholdNanos;

    @GuardedBy("this")
    private ExponentialHistogramSnapshot cachedSnapshot;
    @GuardedBy("this")
    private long lastSnapshot;

    CachingHistogramSnapshot(StripedExponentialHistogram histogram, Ticker ticker, long snapshotThresholdNanos)
    {
        this.histogram = requireNonNull(histogram, "histogram is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
        this.snapshotThresholdNanos = snapshotThresholdNanos;
        lastSnapshot = ticker.read(); // do not snapshot immediately
    }

    synchronized ExponentialHistogramSnapshot snapshot(boolean forceSnapshot)
    {
        if (forceSnapshot || cachedSnapshot == null || ticker.read() - lastSnapshot >= snapshotThresholdNanos) {
            cachedSnapshot = histogram.snapshot();
            lastSnapshot = ticker.read();
        }
        return cachedSnapshot;
    }

    synchronized void reset()
    {
        histogram.reset();
        cachedSnapshot = null;
        lastSnapshot = ticker.read();
    }
}

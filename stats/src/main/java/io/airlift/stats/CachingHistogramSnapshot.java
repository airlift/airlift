package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.time.Instant;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

final class CachingHistogramSnapshot
{
    private final StripedExponentialHistogram histogram;
    private final Ticker ticker;
    private final long snapshotThresholdNanos;
    private final long epochNanoOffset;

    @GuardedBy("this")
    private TimedHistogramSnapshot cachedSnapshot;
    @GuardedBy("this")
    private long startEpochNanos;
    @GuardedBy("this")
    private long lastSnapshot;

    CachingHistogramSnapshot(StripedExponentialHistogram histogram, Ticker ticker, long snapshotThresholdNanos)
    {
        this(histogram, ticker, snapshotThresholdNanos, epochNanos());
    }

    CachingHistogramSnapshot(StripedExponentialHistogram histogram, Ticker ticker, long snapshotThresholdNanos, long startEpochNanos)
    {
        this.histogram = requireNonNull(histogram, "histogram is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
        this.snapshotThresholdNanos = snapshotThresholdNanos;
        this.startEpochNanos = startEpochNanos;
        lastSnapshot = ticker.read(); // do not snapshot immediately
        epochNanoOffset = epochNanos() - lastSnapshot;
    }

    ExponentialHistogramSnapshot snapshot(boolean forceSnapshot)
    {
        return timedSnapshot(forceSnapshot).histogram();
    }

    synchronized TimedHistogramSnapshot timedSnapshot(boolean forceSnapshot)
    {
        if (forceSnapshot || cachedSnapshot == null || ticker.read() - lastSnapshot >= snapshotThresholdNanos) {
            cachedSnapshot = uncachedSnapshot();
            lastSnapshot = ticker.read();
        }
        return cachedSnapshot;
    }

    synchronized TimedHistogramSnapshot uncachedSnapshot()
    {
        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        return new TimedHistogramSnapshot(snapshot, startEpochNanos, max(startEpochNanos, epochNanoOffset + ticker.read()));
    }

    synchronized void reset()
    {
        histogram.reset();
        long epochNanos = epochNanoOffset + ticker.read();
        startEpochNanos = max(epochNanos, max(startEpochNanos, cachedSnapshot == null ? startEpochNanos : cachedSnapshot.epochNanos()) + 1);
        cachedSnapshot = null;
        lastSnapshot = ticker.read();
    }

    private static long epochNanos()
    {
        Instant now = Instant.now();
        return SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
    }
}

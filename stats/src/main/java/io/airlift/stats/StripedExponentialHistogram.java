package io.airlift.stats;

import com.google.errorprone.annotations.ThreadSafe;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;

@ThreadSafe
public final class StripedExponentialHistogram
{
    private static final int DEFAULT_STRIPES = clamp(Runtime.getRuntime().availableProcessors(), 2, 16);

    private final ExponentialHistogram[] stripes;
    private final int maxBuckets;

    public StripedExponentialHistogram()
    {
        this(ExponentialHistogram.DEFAULT_SCALE, ExponentialHistogram.DEFAULT_MAX_BUCKETS, DEFAULT_STRIPES);
    }

    public StripedExponentialHistogram(int scale, int maxBuckets)
    {
        this(scale, maxBuckets, DEFAULT_STRIPES);
    }

    public StripedExponentialHistogram(int scale, int maxBuckets, int stripes)
    {
        checkArgument(stripes > 0, "stripes must be positive");
        this.stripes = new ExponentialHistogram[stripes];
        this.maxBuckets = maxBuckets;
        for (int i = 0; i < stripes; i++) {
            this.stripes[i] = new ExponentialHistogram(scale, maxBuckets);
        }
    }

    StripedExponentialHistogram(ExponentialHistogramSnapshot snapshot, int maxBuckets)
    {
        this.stripes = new ExponentialHistogram[] {new ExponentialHistogram(snapshot, maxBuckets)};
        this.maxBuckets = maxBuckets;
    }

    public void record(double value)
    {
        stripe().record(value);
    }

    public void record(double value, long occurrences)
    {
        stripe().record(value, occurrences);
    }

    public ExponentialHistogramSnapshot snapshot()
    {
        ExponentialHistogramSnapshot[] snapshots = Arrays.stream(stripes)
                .map(ExponentialHistogram::snapshot)
                .toArray(ExponentialHistogramSnapshot[]::new);

        ExponentialHistogramSnapshot merged = ExponentialHistogramSnapshot.merge(Arrays.asList(snapshots), maxBuckets);
        for (int i = 0; i < snapshots.length; i++) {
            if (snapshots[i].scale() != merged.scale()) {
                stripes[i].downscaleToAtMost(merged.scale());
            }
        }
        return merged;
    }

    public void reset()
    {
        for (ExponentialHistogram stripe : stripes) {
            stripe.reset();
        }
    }

    private ExponentialHistogram stripe()
    {
        return stripes[floorMod(Thread.currentThread().threadId(), stripes.length)];
    }
}

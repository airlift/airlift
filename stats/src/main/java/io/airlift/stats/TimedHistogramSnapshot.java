package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import static java.util.Objects.requireNonNull;

public record TimedHistogramSnapshot(ExponentialHistogramSnapshot histogram, long startEpochNanos, long epochNanos)
{
    public TimedHistogramSnapshot
    {
        requireNonNull(histogram, "histogram is null");
    }
}

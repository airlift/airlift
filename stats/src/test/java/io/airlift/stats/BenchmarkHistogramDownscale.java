package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class BenchmarkHistogramDownscale
{
    @Param({"160", "4096"})
    public int buckets;

    @Param({"1", "2", "3", "4", "8"})
    public int reduction;

    @Param({"0", "1"})
    public int offset;

    private ExponentialHistogramSnapshot snapshot;

    @Setup
    public void setup()
    {
        long[] counts = new long[buckets];
        Arrays.fill(counts, 1);
        snapshot = new ExponentialHistogramSnapshot(
                8,
                buckets * 2L,
                0,
                -1,
                1,
                0,
                new Buckets(offset, counts),
                new Buckets(-offset, counts));
    }

    @Benchmark
    public ExponentialHistogramSnapshot downscale()
    {
        return snapshot.downscaleToAtMost(8 - reduction);
    }
}

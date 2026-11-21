package io.airlift.stats;

import com.google.common.collect.ImmutableList;
import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;
import java.util.List;

@State(Scope.Thread)
public class BenchmarkHistogramMerge
{
    @Param({"160", "4096"})
    public int buckets;

    @Param({"2", "16"})
    public int sources;

    @Param({"0", "2"})
    public int scaleReduction;

    private List<ExponentialHistogramSnapshot> snapshots;

    @Setup
    public void setup()
    {
        ImmutableList.Builder<ExponentialHistogramSnapshot> list = ImmutableList.builder();
        for (int i = 0; i < sources; i++) {
            long[] counts = new long[buckets];
            Arrays.fill(counts, 1);
            list.add(new ExponentialHistogramSnapshot(
                    8 + (i % 2) * scaleReduction,
                    buckets,
                    buckets,
                    1,
                    1,
                    0,
                    new Buckets(-buckets / 2 + i, counts),
                    new Buckets(0, new long[0])));
        }
        snapshots = list.build();
    }

    @Benchmark
    public ExponentialHistogramSnapshot merge()
    {
        return ExponentialHistogramSnapshot.merge(snapshots, buckets + sources);
    }
}

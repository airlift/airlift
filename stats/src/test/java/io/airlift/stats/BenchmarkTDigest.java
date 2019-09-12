package io.airlift.stats;

import io.airlift.slice.Slice;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(3)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class BenchmarkTDigest
{
    private static final int NUMBER_OF_ENTRIES = 100_000;

    @State(Scope.Thread)
    public static class Data
    {
        private long[] values1;
        private long[] values2;

        @Setup
        public void setup()
        {
            values1 = makeValues(NUMBER_OF_ENTRIES);
            values2 = makeValues(NUMBER_OF_ENTRIES);
        }

        private long[] makeValues(int size)
        {
            long[] values = new long[size];
            for (int i = 0; i < size; i++) {
                // generate values from a large domain but not many distinct values
                long value = Math.abs((long) (ThreadLocalRandom.current().nextGaussian() * 1_000_000_000));
                values[i] = (value / 1_000_000) * 1_000_000;
            }

            return values;
        }
    }

    @State(Scope.Thread)
    public static class Digest
    {
        private TDigest digest1;
        private TDigest digest2;
        private Slice serializedDigest;

        @Setup
        public void setup(Data data)
        {
            digest1 = makeDigest(data.values1);
            digest2 = makeDigest(data.values2);
            serializedDigest = digest1.serialize();
        }

        private TDigest makeDigest(long[] values)
        {
            TDigest result = new TDigest();
            for (long value : values) {
                result.add(value);
            }
            return result;
        }
    }

    @Benchmark
    @OperationsPerInvocation(NUMBER_OF_ENTRIES)
    public TDigest benchmarkInserts(Data data)
    {
        TDigest digest = new TDigest();

        for (long value : data.values1) {
            digest.add(value);
        }

        return digest;
    }

    @Benchmark
    public TDigest benchmarkCopy(Digest data)
    {
        return TDigest.copyOf(data.digest1);
    }

    @Benchmark
    public TDigest benchmarkMerge(Digest data)
    {
        TDigest merged = TDigest.copyOf(data.digest1);
        merged.mergeWith(data.digest2);
        return merged;
    }

    @Benchmark
    public TDigest benchmarkDeserialize(Digest data)
    {
        return TDigest.deserialize(data.serializedDigest);
    }

    @Benchmark
    public Slice benchmarkSerialize(Digest data)
    {
        return data.digest1.serialize();
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkTDigest.class.getSimpleName() + "\\..*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}

package io.airlift.stats;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
public class BenchmarkExponentialHistogram
{
    private static final int NUMBER_OF_ENTRIES = 100_000;

    @State(Scope.Thread)
    public static class Data
    {
        @Param({"zero", "narrow", "wide", "extreme"})
        private String distribution;

        private double[] values;

        @Setup
        public void setup()
        {
            values = makeValues(distribution);
        }

        private static double[] makeValues(String distribution)
        {
            return switch (distribution) {
                case "narrow" -> makeNarrowValues();
                case "zero" -> new double[NUMBER_OF_ENTRIES];
                case "wide" -> makeWideValues();
                case "extreme" -> makeExtremeValues();
                default -> throw new IllegalArgumentException("unsupported distribution: " + distribution);
            };
        }

        private static double[] makeNarrowValues()
        {
            double[] values = new double[NUMBER_OF_ENTRIES];
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < values.length; i++) {
                values[i] = 1_000_000 + random.nextInt(100_000);
            }
            return values;
        }

        private static double[] makeWideValues()
        {
            double[] values = new double[NUMBER_OF_ENTRIES];
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < values.length; i++) {
                values[i] = Math.pow(2, random.nextDouble(-20, 40));
            }
            return values;
        }

        private static double[] makeExtremeValues()
        {
            double[] values = makeWideValues();
            for (int i = 0; i < values.length; i += 10_000) {
                values[i] = Double.MIN_VALUE;
                values[i + 1] = Double.MAX_VALUE;
            }
            return values;
        }
    }

    @State(Scope.Thread)
    public static class HistogramState
    {
        private ExponentialHistogram histogram;
        private StripedExponentialHistogram stripedHistogram;
        private TimeDistribution timeDistribution;
        private TimeStat timeStat;

        @Setup
        public void setup(Data data)
        {
            histogram = new ExponentialHistogram();
            stripedHistogram = new StripedExponentialHistogram();
            timeDistribution = new TimeDistribution(TimeUnit.NANOSECONDS);
            timeStat = new TimeStat(TimeUnit.NANOSECONDS);
            for (double value : data.values) {
                long nanos = toNanos(value);
                histogram.record(value);
                stripedHistogram.record(value);
                timeDistribution.add(nanos);
                timeStat.addNanos(nanos);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(NUMBER_OF_ENTRIES)
    public ExponentialHistogram benchmarkExponentialHistogramInserts(Data data)
    {
        ExponentialHistogram histogram = new ExponentialHistogram();
        for (double value : data.values) {
            histogram.record(value);
        }
        return histogram;
    }

    @Benchmark
    @OperationsPerInvocation(NUMBER_OF_ENTRIES)
    public ExponentialHistogram benchmarkExponentialHistogramScaleZeroInserts(Data data)
    {
        ExponentialHistogram histogram = new ExponentialHistogram(0, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        for (double value : data.values) {
            histogram.record(value);
        }
        return histogram;
    }

    @Benchmark
    @OperationsPerInvocation(NUMBER_OF_ENTRIES)
    public TimeDistribution benchmarkTimeDistributionInserts(Data data)
    {
        TimeDistribution distribution = new TimeDistribution(TimeUnit.NANOSECONDS);
        for (double value : data.values) {
            distribution.add(toNanos(value));
        }
        return distribution;
    }

    @Benchmark
    @OperationsPerInvocation(NUMBER_OF_ENTRIES)
    public TimeStat benchmarkTimeStatInserts(Data data)
    {
        TimeStat stat = new TimeStat(TimeUnit.NANOSECONDS);
        for (double value : data.values) {
            stat.addNanos(toNanos(value));
        }
        return stat;
    }

    @Benchmark
    public ExponentialHistogram.ExponentialHistogramSnapshot benchmarkExponentialHistogramSnapshot(HistogramState state)
    {
        return state.histogram.snapshot();
    }

    @Benchmark
    public ExponentialHistogram.ExponentialHistogramSnapshot benchmarkStripedExponentialHistogramSnapshot(HistogramState state)
    {
        return state.stripedHistogram.snapshot();
    }

    @Benchmark
    public TimeDistribution.TimeDistributionSnapshot benchmarkTimeDistributionSnapshot(HistogramState state)
    {
        return state.timeDistribution.snapshot();
    }

    @Benchmark
    public TimeStat.TimeDistributionStatSnapshot benchmarkTimeStatSnapshot(HistogramState state)
    {
        return state.timeStat.snapshot();
    }

    private static long toNanos(double value)
    {
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkExponentialHistogram.class.getSimpleName() + "\\..*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}

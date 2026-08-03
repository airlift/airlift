package io.airlift.stats;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(3)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class BenchmarkDecayCounter
{
    @State(Scope.Benchmark)
    public static class Counters
    {
        private final DecayCounter counter = new DecayCounter(DecayConfig.oneMinute());
        private final CounterStat counterStat = new CounterStat();
    }

    @Benchmark
    public void add(Counters counters)
    {
        counters.counter.add(1);
    }

    @Benchmark
    public void updateCounterStat(Counters counters)
    {
        counters.counterStat.update(1);
    }

    @Benchmark
    @Group("mixed")
    @GroupThreads(7)
    public void mixedAdd(Counters counters)
    {
        counters.counter.add(1);
    }

    @Benchmark
    @Group("mixed")
    @GroupThreads(1)
    public double mixedGetCount(Counters counters)
    {
        return counters.counter.getCount();
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkDecayCounter.class.getSimpleName() + "\\..*")
                .build();

        new Runner(options).run();
    }
}

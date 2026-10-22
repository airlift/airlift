/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.stats;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Update-path throughput of the stat containers as they are used on request and split hot paths.
 */
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(3)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Threads(8)
public class BenchmarkStats
{
    @State(Scope.Benchmark)
    public static class Stats
    {
        private final TimeStat timeStat = new TimeStat();
        private final DistributionStat distributionStat = new DistributionStat();
        private final CounterStat counterStat = new CounterStat();
    }

    @Benchmark
    public void timeStatAdd(Stats stats)
    {
        stats.timeStat.add(1234, NANOSECONDS);
    }

    @Benchmark
    public void distributionStatAdd(Stats stats)
    {
        stats.distributionStat.add(1234);
    }

    @Benchmark
    public void counterStatUpdate(Stats stats)
    {
        stats.counterStat.update(1);
    }

    @Benchmark
    public Distribution newDistribution()
    {
        return new Distribution();
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkStats.class.getSimpleName() + "\\..*")
                .build();

        new Runner(options).run();
    }
}

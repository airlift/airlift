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
package io.airlift.stats.cardinality;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(5)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkSparseHll
{
    @Benchmark
    public SparseHll benchmarkInsert(Data data)
    {
        SparseHll instance = new SparseHll(11);
        for (long hash : data.hashes) {
            instance.insertHash(hash);
        }
        return instance;
    }

    @Benchmark
    public SparseHll benchmarkDuplicateInsert(Data data)
    {
        for (long hash : data.hashes) {
            data.instance.insertHash(hash);
        }
        return data.instance;
    }

    @Benchmark
    public HyperLogLog benchmarkInsertThroughDenseTransition(Data data)
    {
        HyperLogLog instance = HyperLogLog.newInstance(2048);
        for (long hash : data.hashes) {
            instance.addHash(hash);
        }
        return instance;
    }

    @State(Scope.Thread)
    public static class Data
    {
        @Param({"10", "100", "500", "2000"})
        public int count;

        public SparseHll instance;
        public long[] hashes;

        @Setup
        public void initialize()
        {
            instance = new SparseHll(11);
            hashes = new long[count];
            Random random = new Random(0);
            for (int i = 0; i < hashes.length; i++) {
                hashes[i] = random.nextLong();
                instance.insertHash(hashes[i]);
            }
        }
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkSparseHll.class.getSimpleName() + ".*")
                .build();

        new Runner(options).run();
    }
}

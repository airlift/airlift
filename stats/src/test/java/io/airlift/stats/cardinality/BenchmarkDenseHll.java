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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(5)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkDenseHll
{
    private static final int LARGE_CARDINALITY = 1_000_000;
    private static final int SMALL_CARDINALITY = 100;

    @Benchmark
    public DenseHll benchmarkInsert(InsertData data)
    {
        for (long hash : data.hashes) {
            data.instance.insertHash(hash);
        }

        return data.instance;
    }

    @Benchmark
    public DenseHll benchmarkMergeWithDense(MergeWithDenseData data)
    {
        return data.base.mergeWith(data.toMerge);
    }

    @Benchmark
    public DenseHll benchmarkMergeWithSparse(MergeWithSparseData data)
    {
        return data.base.mergeWith(data.toMerge);
    }

    @State(Scope.Thread)
    public static class InsertData
    {
        public final DenseHll instance = new DenseHll(12);
        public final long[] hashes = new long[500];

        @Setup(Level.Iteration)
        public void initialize()
        {
            for (int i = 0; i < hashes.length; i++) {
                hashes[i] = ThreadLocalRandom.current().nextLong();
            }
        }
    }

    @State(Scope.Thread)
    public static class MergeWithDenseData
    {
        public DenseHll base;
        public DenseHll toMerge;

        @Setup(Level.Iteration)
        public void initialize()
        {
            base = new DenseHll(12);
            for (int i = 0; i < LARGE_CARDINALITY; i++) {
                base.insertHash(ThreadLocalRandom.current().nextLong());
            }

            // Small cardinality so we can do an apples-to-apples comparison
            // between dense/dense vs dense/sparse merge. Sparse only supports
            // small cardinalities.
            toMerge = new DenseHll(12);
            for (int i = 0; i < SMALL_CARDINALITY; i++) {
                toMerge.insertHash(ThreadLocalRandom.current().nextLong());
            }
        }
    }

    @State(Scope.Thread)
    public static class MergeWithSparseData
    {
        public DenseHll base;
        public SparseHll toMerge;

        @Setup(Level.Iteration)
        public void initialize()
        {
            base = new DenseHll(12);
            for (int i = 0; i < LARGE_CARDINALITY; i++) {
                base.insertHash(ThreadLocalRandom.current().nextLong());
            }

            toMerge = new SparseHll(12);
            for (int i = 0; i < SMALL_CARDINALITY; i++) {
                toMerge.insertHash(ThreadLocalRandom.current().nextLong());
            }
        }
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkDenseHll.class.getSimpleName() + ".*")
                .build();

        new Runner(options).run();
    }
}

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
package io.airlift.concurrent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.concurrent.MoreFutures.whenAnyCompleteCancelOthers;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BenchmarkWhenAnyCompleteCancelOthers
{
    //        with optimization in ExtendedSettableFuture.setAsync    without
    // 300                    32.7 +/- 1.7 us                     810.6 +/- 31 us
    // 1000                  106.2 +/- 3.0 us                    2813.5 +/- 121 us

    @Param({"300", "1000"})
    private int futureCount;

    @Benchmark
    public void benchmark()
            throws Exception
    {
        Semaphore semaphore = new Semaphore(futureCount);

        ArrayList<SettableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < futureCount; i++) {
            SettableFuture<?> future = SettableFuture.create();
            future.addListener(() -> semaphore.release(1), directExecutor());
            futures.add(future);
        }
        ListenableFuture<?> anyComplete = whenAnyCompleteCancelOthers(futures);
        futures.get(futureCount / 2).set(null);
        semaphore.acquireUninterruptibly(futureCount);
        anyComplete.get();
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkWhenAnyCompleteCancelOthers.class.getSimpleName() + ".*")
                .build();

        new Runner(options).run();
    }
}

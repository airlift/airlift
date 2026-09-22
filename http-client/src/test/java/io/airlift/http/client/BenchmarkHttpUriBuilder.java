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
package io.airlift.http.client;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Warmup(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkHttpUriBuilder
{
    // Representative of the internal URIs Trino builds per request: all-ASCII, nothing to encode.
    private static final URI BASE = URI.create("http://worker.example.com:8080/v1/task");

    @Benchmark
    public URI benchmarkBuildTaskStatusUri()
    {
        return uriBuilderFrom(BASE)
                .appendPath("20231201_120000_00001_abcde.1.2.3")
                .appendPath("status")
                .addParameter("summarize")
                .addParameter("maxWait", "1000ms")
                .build();
    }

    @Benchmark
    public String benchmarkToStringSimplePath()
    {
        return uriBuilderFrom(BASE)
                .replacePath("/v1/task/20231201_120000_00001_abcde.1.2.3/status")
                .toString();
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkHttpUriBuilder.class.getSimpleName() + ".*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}

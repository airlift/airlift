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

import com.google.common.collect.ListMultimap;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Warmup(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkResponseHeaders
{
    private HttpFields headers;

    @Setup
    public void setup()
    {
        // Representative of a typical internal service response: well-known headers plus a few custom ones.
        headers = HttpFields.build()
                .add(HttpHeader.CONTENT_TYPE, "application/json")
                .add(HttpHeader.CONTENT_LENGTH, 1234)
                .add(HttpHeader.DATE, "Wed, 21 Oct 2015 07:28:00 GMT")
                .add(HttpHeader.SERVER, "Airlift")
                .add(HttpHeader.CONNECTION, "keep-alive")
                .add(new HttpField("X-Trino-Task-Instance-Id", "20231201_120000_00001_abcde.1.2.3"))
                .add(new HttpField("X-Trino-Page-Token", "42"))
                .add(new HttpField("X-Trino-Buffer-Complete", "false"))
                .asImmutable();
    }

    @Benchmark
    public ListMultimap<HeaderName, String> benchmarkToHeadersMap()
    {
        return Response.toHeadersMap(headers);
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkResponseHeaders.class.getSimpleName() + ".*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}

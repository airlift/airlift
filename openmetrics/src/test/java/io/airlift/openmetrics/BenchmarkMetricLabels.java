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
package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

@State(Scope.Thread)
public class BenchmarkMetricLabels
{
    @Param({"1", "1000"})
    public int distinctLabels;

    private Map<String, List<Metric>> families;

    @Setup
    public void setup()
    {
        ImmutableList.Builder<Metric> metrics = ImmutableList.builder();
        for (int i = 0; i < 1000; i++) {
            metrics.add(new Gauge("metric_" + i, i, Map.of(
                    "node", "node_" + (i % distinctLabels),
                    "environment", "production",
                    "service", "test\\service\n"), ""));
        }
        families = MetricsUtils.groupMetricFamilies(metrics.build());
    }

    @Benchmark
    public void writeLabels()
            throws IOException
    {
        MetricsUtils.writeMetricFamilies(Writer.nullWriter(), families);
    }
}

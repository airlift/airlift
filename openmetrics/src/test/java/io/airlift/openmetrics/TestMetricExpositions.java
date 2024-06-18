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

import com.google.common.collect.ImmutableMap;
import io.airlift.openmetrics.types.BigCounter;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Info;
import io.airlift.openmetrics.types.Summary;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricExpositions
{
    @Test
    public void testCounterExposition()
    {
        String expected = """
                # TYPE metric_name counter
                # HELP metric_name metric_help
                metric_name 0
                """;

        Counter counter = new Counter("metric_name", 0, ImmutableMap.of(), "metric_help");
        BigCounter bigCounter = new BigCounter("metric_name", BigInteger.ZERO, ImmutableMap.of(), "metric_help");
        assertThat(counter.getMetricExposition()).isEqualTo(bigCounter.getMetricExposition());
        assertThat(counter.getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testCounterExpositionLabels()
    {
        String expected = """
                # TYPE metric_name counter
                # HELP metric_name metric_help
                metric_name{type="cavendish"} 0
                """;

        Counter counter = new Counter("metric_name", 0, ImmutableMap.of("type", "cavendish"), "metric_help");
        BigCounter bigCounter = new BigCounter("metric_name", BigInteger.ZERO, ImmutableMap.of("type", "cavendish"), "metric_help");
        assertThat(counter.getMetricExposition()).isEqualTo(bigCounter.getMetricExposition());
        assertThat(counter.getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testGaugeExposition()
    {
        String expected = """
                # TYPE metric_name gauge
                # HELP metric_name metric_help
                metric_name 0.0
                """;

        assertThat(new Gauge("metric_name", 0.0, ImmutableMap.of(), "metric_help").getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testGaugeExpositionLabels()
    {
        String expected = """
                # TYPE metric_name gauge
                # HELP metric_name metric_help
                metric_name{type="cavendish"} 0.0
                """;

        assertThat(new Gauge("metric_name", 0.0, ImmutableMap.of("type", "cavendish"), "metric_help").getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testInfoExposition()
    {
        String expected = """
                # TYPE metric_name info
                # HELP metric_name metric_help
                metric_name banana
                """;

        assertThat(new Info("metric_name", "banana", ImmutableMap.of(), "metric_help").getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testInfoExpositionLabels()
    {
        String expected = """
                # TYPE metric_name info
                # HELP metric_name metric_help
                metric_name{type="cavendish"} banana
                """;

        assertThat(new Info("metric_name", "banana", ImmutableMap.of("type", "cavendish"), "metric_help").getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testSummaryExposition()
    {
        String expected = """
                # TYPE metric_name summary
                # HELP metric_name metric_help
                metric_name_count 10
                metric_name_sum 2.0
                metric_name_created 3.0
                metric_name{quantile="0.5"} 0.25
                """;

        assertThat(new Summary("metric_name", 10L, 2.0, 3.0, ImmutableMap.of(0.5, 0.25), ImmutableMap.of(), "metric_help").getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testSummaryExpositionLabels()
    {
        String expected = """
                # TYPE metric_name summary
                # HELP metric_name metric_help
                metric_name_count{fruit="apple"} 10
                metric_name_sum{fruit="apple"} 2.0
                metric_name_created{fruit="apple"} 3.0
                metric_name{fruit="apple",quantile="0.5"} 0.25
                """;

        assertThat(new Summary("metric_name", 10L, 2.0, 3.0, ImmutableMap.of(0.5, 0.25), ImmutableMap.of("fruit", "apple"), "metric_help").getMetricExposition()).isEqualTo(expected);
    }
}

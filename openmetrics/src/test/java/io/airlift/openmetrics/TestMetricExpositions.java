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
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Info;
import io.airlift.openmetrics.types.Summary;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

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

    @Test
    public void testCompositeMetricExposition()
    {
        String expected = """
                # TYPE metric_name_committed gauge
                # HELP metric_name_committed metric_help
                metric_name_committed 200.0
                # TYPE metric_name_max gauge
                # HELP metric_name_max metric_help
                metric_name_max 1000.0
                # TYPE metric_name_used gauge
                # HELP metric_name_used metric_help
                metric_name_used 100.0
                """;

        CompositeData compositeData = createMemoryUsageCompositeData(100L, 200L, 1000L);
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", compositeData, ImmutableMap.of(), "metric_help");
        assertThat(compositeMetric.getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testCompositeMetricExpositionLabels()
    {
        String expected = """
                # TYPE metric_name_committed gauge
                # HELP metric_name_committed metric_help
                metric_name_committed{type="cavendish"} 200.0
                # TYPE metric_name_max gauge
                # HELP metric_name_max metric_help
                metric_name_max{type="cavendish"} 1000.0
                # TYPE metric_name_used gauge
                # HELP metric_name_used metric_help
                metric_name_used{type="cavendish"} 100.0
                """;

        CompositeData compositeData = createMemoryUsageCompositeData(100L, 200L, 1000L);
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", compositeData, ImmutableMap.of("type", "cavendish"), "metric_help");
        assertThat(compositeMetric.getMetricExposition()).isEqualTo(expected);
    }

    private CompositeData createMemoryUsageCompositeData(long used, long committed, long max)
    {
        try {
            String[] itemNames = {"used", "committed", "max"};
            OpenType<?>[] itemTypes = {SimpleType.LONG, SimpleType.LONG, SimpleType.LONG};
            CompositeType compositeType = new CompositeType("MemoryUsage", "Memory Usage", itemNames, itemNames, itemTypes);

            Map<String, Object> values = new HashMap<>();
            values.put("used", used);
            values.put("committed", committed);
            values.put("max", max);

            return new CompositeDataSupport(compositeType, values);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTabularDataExposition()
    {
        String expected = """
                # TYPE metric_name_value gauge
                # HELP metric_name_value metric_help
                metric_name_value{region="us-east",zone="1a"} 100.0
                metric_name_value{region="us-west",zone="2b"} 200.0
                """;

        TabularData tabularData = createTestTabularData();
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", tabularData, ImmutableMap.of(), "metric_help");
        assertThat(compositeMetric.getMetricExposition()).isEqualTo(expected);
    }

    @Test
    public void testTabularDataExpositionLabels()
    {
        String expected = """
                # TYPE metric_name_value gauge
                # HELP metric_name_value metric_help
                metric_name_value{region="us-east",type="cavendish",zone="1a"} 100.0
                metric_name_value{region="us-west",type="cavendish",zone="2b"} 200.0
                """;

        TabularData tabularData = createTestTabularData();
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", tabularData, ImmutableMap.of("type", "cavendish"), "metric_help");
        assertThat(compositeMetric.getMetricExposition()).isEqualTo(expected);
    }

    private TabularData createTestTabularData()
    {
        try {
            String[] itemNames = {"region", "zone", "value"};
            OpenType<?>[] itemTypes = {SimpleType.STRING, SimpleType.STRING, SimpleType.LONG};
            CompositeType compositeType = new CompositeType("TestData", "Test Data", itemNames, itemNames, itemTypes);

            String[] indexNames = {"region", "zone"};
            TabularType tabularType = new TabularType("TestTable", "Test Table", compositeType, indexNames);

            TabularDataSupport tabularData = new TabularDataSupport(tabularType);

            Map<String, Object> row1 = new HashMap<>();
            row1.put("region", "us-east");
            row1.put("zone", "1a");
            row1.put("value", 100L);
            tabularData.put(new CompositeDataSupport(compositeType, row1));

            Map<String, Object> row2 = new HashMap<>();
            row2.put("region", "us-west");
            row2.put("zone", "2b");
            row2.put("value", 200L);
            tabularData.put(new CompositeDataSupport(compositeType, row2));

            return tabularData;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

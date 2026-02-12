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
import com.google.common.collect.ImmutableMap;
import io.airlift.node.NodeInfo;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.stats.labeled.GlobalLabeledStatRegistry;
import io.airlift.stats.labeled.LabelSet;
import io.airlift.stats.labeled.LabeledCounterStat;
import io.airlift.stats.labeled.LabeledGaugeStat;
import io.airlift.stats.labeled.LabeledHistogramStat;
import io.airlift.stats.labeled.LabeledStatConfig;
import io.airlift.stats.labeled.LabeledStatRegistry;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import java.util.List;

import static io.airlift.openmetrics.MetricsResource.sanitizeMetricName;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricsResource
{
    @Test
    public void testSanitizeMetricName()
    {
        String original = "com.zaxxer.hikari:type=Pool (HikariPool-1),ThreadsAwaitingConnection";
        String expected = "com_zaxxer_hikari_type_Pool_HikariPool_1_ThreadsAwaitingConnection";
        assertThat(sanitizeMetricName(original)).isEqualTo(expected);
    }

    @Test
    public void testMetricsWithSameNameButDifferingLabels()
    {
        Counter counterOne = new Counter("test_metric", 42, ImmutableMap.of("a", "123", "b", "456"), "Help text one");
        Gauge gauge = new Gauge("another_metric", 1, ImmutableMap.of("a", "456", "b", "789"), "Help text");
        Counter counterTwo = new Counter("test_metric", 12, ImmutableMap.of("a", "456", "b", "789"), "Help text two");

        String metricExpositions = managedMetricExpositions(ImmutableList.of(counterOne, gauge, counterTwo));
        assertThat(metricExpositions).isEqualTo("""
            # TYPE another_metric gauge
            # HELP another_metric Help text
            another_metric{a="456",b="789"} 1.0
            # TYPE test_metric counter
            # HELP test_metric Help text one
            test_metric{a="123",b="456"} 42
            test_metric{a="456",b="789"} 12
            """.stripIndent());
    }

    @Test
    public void testInvalidMetricCollision()
    {
        // A counter and a gauge registered with the same name but different types is invalid
        // This is unlikely to happen in practice because JMX enforces uniqueness at the metric name level
        Counter counter = new Counter("test_metric.abc", 42, ImmutableMap.of(), "Help counter");
        Gauge gauge = new Gauge("test_metric.abc", 12, ImmutableMap.of(), "Help gauge");

        String metricExpositions = managedMetricExpositions(ImmutableList.of(counter, gauge));
        assertThat(metricExpositions).isEqualTo("""
            # TYPE test_metric.abc counter
            # HELP test_metric.abc Help counter
            test_metric.abc 42
            test_metric.abc 12.0
            """.stripIndent());
    }

    @Test
    public void testDuplicateMetricDescriptorPublished()
    {
        // Note counter collides with submetric of the same name in the composite metric
        // TODO: consider de-duplicating invalid metrics instead of allowing them to be published, this can happen in practice
        Counter counter = new Counter("test_metric.abc", 42, ImmutableMap.of(), "Help text");
        Counter subMetric = new Counter("test_metric.abc", 12, ImmutableMap.of(), "Help text");
        CompositeMetric compositeMetric = new CompositeMetric("test_metric", ImmutableMap.of(), "Help text", ImmutableList.of(subMetric));

        String metricExpositions = managedMetricExpositions(ImmutableList.of(counter, compositeMetric));
        // For determinism, metric expositions are sorted by metric name
        assertThat(metricExpositions).isEqualTo("""
            # TYPE test_metric.abc counter
            # HELP test_metric.abc Help text
            test_metric.abc 12
            # TYPE test_metric.abc counter
            # HELP test_metric.abc Help text
            test_metric.abc 42
            """.stripIndent());
    }

    @Test
    public void testLabeledCounterStat()
    {
        MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
        MBeanExporter mBeanExporter = new MBeanExporter(mBeanServer);
        LabeledStatRegistry labeledStatRegistry = new GlobalLabeledStatRegistry(mBeanExporter, new LabeledStatConfig());

        LabeledCounterStat recordCountCounter = labeledStatRegistry.labeledCounter(
                "io.airlift.record_count",
                "Faking a counter of records processed");
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "1")));
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2")));
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2")));

        LabeledCounterStat bytesIngestedCounter = labeledStatRegistry.labeledCounter(
                "io.airlift.bytes_ingested",
                "Faking a counter of bytes ingested");
        bytesIngestedCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "1", "b", "xxx")));
        bytesIngestedCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2", "b", "yyy")));
        bytesIngestedCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2", "b", "yyy")));
        bytesIngestedCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2", "b", "zzz")));
        bytesIngestedCounter.increment(LabelSet.fromLabels(ImmutableMap.of("a", "2", "b", "zzz")));

        MetricsConfig metricsConfig = new MetricsConfig();
        MetricsResource resource = new MetricsResource(mBeanServer, mBeanExporter, metricsConfig, new NodeInfo("test"));

        String actual = resource.getMetrics(ImmutableList.of());
        String expected = """
            # TYPE io.airlift.bytes_ingested counter
            # HELP io.airlift.bytes_ingested Faking a counter of bytes ingested
            io.airlift.bytes_ingested{a="1",b="xxx"} 1
            io.airlift.bytes_ingested{a="2",b="yyy"} 2
            io.airlift.bytes_ingested{a="2",b="zzz"} 2
            # TYPE io.airlift.record_count counter
            # HELP io.airlift.record_count Faking a counter of records processed
            io.airlift.record_count{a="1"} 1
            io.airlift.record_count{a="2"} 2
            # EOF
            """.stripIndent();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testLabeledGaugeStat()
    {
        MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
        MBeanExporter mBeanExporter = new MBeanExporter(mBeanServer);
        LabeledStatRegistry labeledStatRegistry = new GlobalLabeledStatRegistry(mBeanExporter, new LabeledStatConfig());

        LabeledGaugeStat cpuLoadGauge = labeledStatRegistry.labeledGauge(
                "io.airlift.cpu_load",
                "Faking a gauge of CPU load");
        cpuLoadGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host1")), 0.5);
        cpuLoadGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host1")), 0.7);
        cpuLoadGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host2")), 0.9);

        LabeledGaugeStat heapMemoryGauge = labeledStatRegistry.labeledGauge(
                "io.airlift.heap_memory_usage",
                "Faking a gauge of heap memory usage");
        heapMemoryGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host1", "region", "us-east-1")), 32.0);
        heapMemoryGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host2", "region", "us-east-2")), 16.0);
        heapMemoryGauge.set(LabelSet.fromLabels(ImmutableMap.of("host", "host1", "region", "us-east-1")), 36.0);

        MetricsConfig metricsConfig = new MetricsConfig();
        MetricsResource resource = new MetricsResource(mBeanServer, mBeanExporter, metricsConfig, new NodeInfo("test"));

        String actual = resource.getMetrics(ImmutableList.of());

        String expected = """
            # TYPE io.airlift.cpu_load gauge
            # HELP io.airlift.cpu_load Faking a gauge of CPU load
            io.airlift.cpu_load{host="host1"} 0.7
            io.airlift.cpu_load{host="host2"} 0.9
            # TYPE io.airlift.heap_memory_usage gauge
            # HELP io.airlift.heap_memory_usage Faking a gauge of heap memory usage
            io.airlift.heap_memory_usage{host="host1",region="us-east-1"} 36.0
            io.airlift.heap_memory_usage{host="host2",region="us-east-2"} 16.0
            # EOF
            """.stripIndent();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testLabeledHistogramStat()
    {
        MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
        MBeanExporter mBeanExporter = new MBeanExporter(mBeanServer);
        LabeledStatRegistry labeledStatRegistry = new GlobalLabeledStatRegistry(mBeanExporter, new LabeledStatConfig());
        LabeledHistogramStat responseHistogram = labeledStatRegistry.labeledHistogram(
                "io.airlift.response_millis",
                "Faking a histogram of response times",
                new double[] {0, 100, 200, 400, 800, 1600, 3200, 6400});

        responseHistogram.observe(LabelSet.fromLabels(ImmutableMap.of("a", "1")), -1);
        responseHistogram.observe(LabelSet.fromLabels(ImmutableMap.of("a", "1")), 50);
        responseHistogram.observe(LabelSet.fromLabels(ImmutableMap.of("a", "1")), 900);
        responseHistogram.observe(LabelSet.fromLabels(ImmutableMap.of("a", "1")), 3200);
        responseHistogram.observe(LabelSet.fromLabels(ImmutableMap.of("a", "1")), 10000);
        responseHistogram.batchObserve(LabelSet.fromLabels(ImmutableMap.of("a", "2")), 150, 4);


        MetricsConfig metricsConfig = new MetricsConfig();
        MetricsResource resource = new MetricsResource(mBeanServer, mBeanExporter, metricsConfig, new NodeInfo("test"));

        String actual = resource.getMetrics(ImmutableList.of());
        String expected = """
            # TYPE io.airlift.response_millis histogram
            # HELP io.airlift.response_millis Faking a histogram of response times
            io.airlift.response_millis_bucket{a="1",le="0.0"} 1
            io.airlift.response_millis_bucket{a="1",le="100.0"} 2
            io.airlift.response_millis_bucket{a="1",le="200.0"} 2
            io.airlift.response_millis_bucket{a="1",le="400.0"} 2
            io.airlift.response_millis_bucket{a="1",le="800.0"} 2
            io.airlift.response_millis_bucket{a="1",le="1600.0"} 3
            io.airlift.response_millis_bucket{a="1",le="3200.0"} 4
            io.airlift.response_millis_bucket{a="1",le="6400.0"} 4
            io.airlift.response_millis_bucket{a="1",le="+Inf"} 5
            io.airlift.response_millis_count{a="1"} 5
            io.airlift.response_millis_sum{a="1"} 14149.0
            io.airlift.response_millis_bucket{a="2",le="0.0"} 0
            io.airlift.response_millis_bucket{a="2",le="100.0"} 0
            io.airlift.response_millis_bucket{a="2",le="200.0"} 4
            io.airlift.response_millis_bucket{a="2",le="400.0"} 4
            io.airlift.response_millis_bucket{a="2",le="800.0"} 4
            io.airlift.response_millis_bucket{a="2",le="1600.0"} 4
            io.airlift.response_millis_bucket{a="2",le="3200.0"} 4
            io.airlift.response_millis_bucket{a="2",le="6400.0"} 4
            io.airlift.response_millis_bucket{a="2",le="+Inf"} 4
            io.airlift.response_millis_count{a="2"} 4
            io.airlift.response_millis_sum{a="2"} 600.0
            # EOF
            """.stripIndent();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testLabeledStatEmbeddedInsideOtherStat()
    {
        MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
        MBeanExporter mBeanExporter = new MBeanExporter(mBeanServer);
        LabeledStatRegistry labeledStatRegistry = new GlobalLabeledStatRegistry(mBeanExporter, new LabeledStatConfig());
        LabeledCounterStat recordCountCounter = labeledStatRegistry.labeledCounter(
                "io.airlift.record_count",
                "Faking a counter of records processed");
        mBeanExporter.exportWithGeneratedName(new MyBean(1, recordCountCounter));
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("region", "us-east-1")));
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("region", "us-west-2")));
        recordCountCounter.increment(LabelSet.fromLabels(ImmutableMap.of("region", "us-east-1")));


        MetricsConfig metricsConfig = new MetricsConfig();
        MetricsResource resource = new MetricsResource(mBeanServer, mBeanExporter, metricsConfig, new NodeInfo("test"));

        String actual = resource.getMetrics(ImmutableList.of());
        assertThat(actual).isEqualTo("""
            # TYPE io.airlift.record_count counter
            # HELP io.airlift.record_count Faking a counter of records processed
            io.airlift.record_count{region="us-east-1"} 2
            io.airlift.record_count{region="us-west-2"} 1
            # TYPE io_airlift_openmetrics_name_MyBean_Count gauge
            io_airlift_openmetrics_name_MyBean_Count 1.0
            # EOF
            """.stripIndent());
    }

    private String managedMetricExpositions(List<Metric> managedMetrics)
    {
        StringBuilder builder = new StringBuilder();
        MetricsResource.managedMetricExpositions(builder, managedMetrics);
        return builder.toString();
    }

    public record MyBean(long count, LabeledCounterStat counter)
    {
        @Managed
        public long getCount()
        {
            return count;
        }

        @Managed
        @Nested
        public LabeledCounterStat getNestedStat()
        {
            return counter;
        }
    }
}

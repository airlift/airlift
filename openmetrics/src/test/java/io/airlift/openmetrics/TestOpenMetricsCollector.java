package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.node.NodeInfo;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.CounterStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.node.NodeConfig.AddressSource.IP;
import static io.airlift.openmetrics.MetricsUtils.renderMetricsExpositions;
import static io.airlift.openmetrics.MetricsUtils.writeMetricsExpositions;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class TestOpenMetricsCollector
{
    @Test
    public void testCollectManagedNumericGauge()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .filteredOn(Gauge.class::isInstance)
                .map(Gauge.class::cast)
                .anySatisfy(metric -> {
                    assertThat(metric.metricName()).endsWith("_NumericGauge");
                    assertThat(metric.value()).isEqualTo(7.0);
                    assertThat(metric.labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
                    assertThat(metric.help()).isEqualTo("numeric gauge");
                });
    }

    @Test
    public void testCollectManagedCounterStat()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .filteredOn(Counter.class::isInstance)
                .map(Counter.class::cast)
                .anySatisfy(metric -> {
                    assertThat(metric.metricName()).endsWith("_Requests");
                    assertThat(metric.value()).isEqualTo(11);
                    assertThat(metric.labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
                    assertThat(metric.help()).isEqualTo("request counter");
                });
    }

    @Test
    public void testCollectManagedTimeDistribution()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .filteredOn(Summary.class::isInstance)
                .map(Summary.class::cast)
                .anySatisfy(metric -> {
                    assertThat(metric.metricName()).endsWith("_Latency");
                    assertThat(metric.count()).isEqualTo(2);
                    assertThat(metric.labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
                    assertThat(metric.help()).isEqualTo("request latency");
                });
    }

    @Test
    public void testCollectConfiguredJmxNumericAttribute()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .filteredOn(Gauge.class::isInstance)
                .map(Gauge.class::cast)
                .anySatisfy(metric -> {
                    assertThat(metric.metricName()).isEqualTo("JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Count");
                    assertThat(metric.value()).isEqualTo(23.0);
                    assertThat(metric.labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
                });
    }

    @Test
    public void testCollectConfiguredJmxCompositeAttribute()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();

        Optional<CompositeMetric> memoryMetric = collector.collect().stream()
                .filter(CompositeMetric.class::isInstance)
                .map(CompositeMetric.class::cast)
                .filter(metric -> metric.metricName().equals("JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Memory"))
                .findFirst();

        assertThat(memoryMetric).isPresent();
        assertThat(memoryMetric.orElseThrow().labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
        assertThat(memoryMetric.orElseThrow().subMetrics())
                .filteredOn(Gauge.class::isInstance)
                .map(Gauge.class::cast)
                .extracting(Gauge::metricName, Gauge::value)
                .contains(
                        tuple("JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Memory_committed", 200.0),
                        tuple("JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Memory_max", 1000.0),
                        tuple("JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Memory_used", 100.0));
    }

    @Test
    public void testResourceOutputMatchesCollectorRendering()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();
        MetricsResource resource = new MetricsResource(collector);

        StringWriter writer = new StringWriter();
        writeMetricsExpositions(writer, collector.collect());
        writer.append("# EOF\n");

        assertThat(getMetrics(resource, ImmutableList.of())).isEqualTo(writer.toString());
    }

    @Test
    public void testFilteredMetricLookupCompatibility()
            throws Exception
    {
        OpenMetricsCollector collector = createTestingCollector();
        MetricsResource resource = new MetricsResource(collector);
        String managedMetricName = collector.collect().stream()
                .map(Metric::metricName)
                .filter(name -> name.endsWith("_NumericGauge"))
                .findFirst()
                .orElseThrow();
        String jmxMetricName = "JMX_io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Count";

        assertThat(collector.collect(ImmutableList.of(managedMetricName)))
                .extracting(Metric::metricName)
                .containsExactly(managedMetricName);
        assertThat(getMetrics(resource, ImmutableList.of(managedMetricName)))
                .isEqualTo(renderMetricsExpositions(collector.collect(ImmutableList.of(managedMetricName)), true));

        Metric filteredJmxMetric = collector.findMetric(jmxMetricName).orElseThrow();
        assertThat(filteredJmxMetric.metricName()).isEqualTo("io_airlift_openmetrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Count");
        assertThat(getMetrics(resource, ImmutableList.of(jmxMetricName)))
                .isEqualTo(renderMetricsExpositions(collector.collect(ImmutableList.of(jmxMetricName)), true));
    }

    private static String getMetrics(MetricsResource resource, List<String> filter)
            throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resource.getMetrics(filter).write(output);
        return output.toString(UTF_8);
    }

    private static OpenMetricsCollector createTestingCollector()
            throws Exception
    {
        MBeanServer mbeanServer = MBeanServerFactory.newMBeanServer();
        MBeanExporter mbeanExporter = new MBeanExporter(mbeanServer);

        ManagedMetrics managedMetrics = new ManagedMetrics();
        managedMetrics.getRequests().update(11);
        managedMetrics.getLatency().add(100);
        managedMetrics.getLatency().add(200);
        managedMetrics.forceLatencyMerge();
        mbeanExporter.export(new ObjectName("io.airlift.openmetrics.test:name=managed"), managedMetrics);

        ObjectName configuredObjectName = new ObjectName("io.airlift.openmetrics.test:name=configured,type=Test");
        mbeanServer.registerMBean(new StandardMBean(new ConfiguredMetrics(), ConfiguredMetricsMBean.class), configuredObjectName);

        MetricsConfig metricsConfig = new MetricsConfig().setJmxObjectNames(ImmutableList.of(configuredObjectName));
        NodeInfo nodeInfo = new NodeInfo(
                "test",
                "pool",
                "nodeInfo",
                null,
                null,
                null,
                null,
                null,
                null,
                IP,
                null,
                ImmutableMap.of("team", "a", "region", "b"),
                false);

        return new OpenMetricsCollector(mbeanServer, mbeanExporter, metricsConfig, nodeInfo);
    }

    public static class ManagedMetrics
    {
        private final CounterStat requests = new CounterStat();
        private final TestingTicker ticker = new TestingTicker();
        private final TimeDistribution latency = new TimeDistribution(ticker);

        @Managed(description = "numeric gauge")
        public int getNumericGauge()
        {
            return 7;
        }

        @Managed(description = "request counter")
        @Nested
        public CounterStat getRequests()
        {
            return requests;
        }

        @Managed(description = "request latency")
        @Nested
        public TimeDistribution getLatency()
        {
            return latency;
        }

        private void forceLatencyMerge()
        {
            ticker.increment(1, SECONDS);
            latency.getCount();
        }
    }

    public interface ConfiguredMetricsMBean
    {
        int getCount();

        CompositeData getMemory();

        String getUnsupported();
    }

    public static class ConfiguredMetrics
            implements ConfiguredMetricsMBean
    {
        @Override
        public int getCount()
        {
            return 23;
        }

        @Override
        public CompositeData getMemory()
        {
            return createMemoryUsageCompositeData(100, 200, 1000);
        }

        @Override
        public String getUnsupported()
        {
            return "ignored";
        }
    }

    private static CompositeData createMemoryUsageCompositeData(long used, long committed, long max)
    {
        try {
            String[] itemNames = {"used", "committed", "max"};
            OpenType<?>[] itemTypes = {SimpleType.LONG, SimpleType.LONG, SimpleType.LONG};
            CompositeType compositeType = new CompositeType("MemoryUsage", "Memory Usage", itemNames, itemNames, itemTypes);

            Map<String, Object> values = ImmutableMap.<String, Object>builder()
                    .put("used", used)
                    .put("committed", committed)
                    .put("max", max)
                    .buildOrThrow();

            return new CompositeDataSupport(compositeType, values);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package io.airlift.metrics;

import com.google.common.collect.ImmutableMap;
import io.airlift.metrics.MetricSource.JmxMetricSource;
import io.airlift.metrics.MetricSource.ManagedMetricSource;
import io.airlift.node.NodeInfo;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.Flatten;
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

import java.util.List;
import java.util.Map;

import static io.airlift.node.NodeConfig.AddressSource.IP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricsCollector
{
    @Test
    public void testCollectManagedNumericMetric()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .anySatisfy(group -> {
                    assertThat(group.source()).isEqualTo(new ManagedMetricSource("io.airlift.metrics.test:name=managed"));
                    assertThat(group.labels()).isEqualTo(ImmutableMap.of("region", "b", "team", "a"));
                    assertThat(group.attributes()).hasSizeGreaterThan(1);
                });

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("NumericGauge");
                    assertThat(metric.value()).isEqualTo(7);
                    assertThat(metric.description()).isEqualTo("numeric gauge");
                });
    }

    @Test
    public void testCollectManagedNestedAttributePaths()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("Count");
                    assertThat(metric.value()).isEqualTo(31);
                })
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("NestedStats", "Count");
                    assertThat(metric.value()).isEqualTo(41);
                });
    }

    @Test
    public void testCollectManagedCounterStat()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("Requests");
                    assertThat(metric.value()).isInstanceOfSatisfying(CounterStat.class, counter -> assertThat(counter.getTotalCount()).isEqualTo(11));
                    assertThat(metric.description()).isEqualTo("request counter");
                });
    }

    @Test
    public void testCollectManagedTimeDistribution()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("Latency");
                    assertThat(metric.value()).isInstanceOfSatisfying(TimeDistribution.class, distribution -> assertThat(distribution.getCount()).isEqualTo(2));
                    assertThat(metric.description()).isEqualTo("request latency");
                });
    }

    @Test
    public void testCollectManagedTimeStat()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("RequestTime");
                    assertThat(metric.value()).isInstanceOfSatisfying(TimeStat.class, stat -> assertThat(stat.getAllTime().getCount()).isEqualTo(2));
                    assertThat(metric.description()).isEqualTo("request time");
                });
    }

    @Test
    public void testCollectManagedDistribution()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("QueuedRequests");
                    assertThat(metric.value()).isInstanceOfSatisfying(Distribution.class, distribution -> assertThat(distribution.getCount()).isEqualTo(2));
                    assertThat(metric.description()).isEqualTo("queued requests");
                });
    }

    @Test
    public void testCollectManagedDistributionStat()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("ReadBytes");
                    assertThat(metric.value()).isInstanceOfSatisfying(DistributionStat.class, stat -> assertThat(stat.getAllTime().getCount()).isEqualTo(2));
                    assertThat(metric.description()).isEqualTo("read bytes");
                });
    }

    @Test
    public void testCollectConfiguredJmxMetrics()
            throws Exception
    {
        MetricsCollector collector = createTestingCollector();

        assertThat(collector.collect())
                .anySatisfy(group -> assertThat(group.source()).isEqualTo(new JmxMetricSource(new ObjectName("io.airlift.metrics.test:name=configured,type=Test"))));

        assertThat(attributes(collector))
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("Count");
                    assertThat(metric.value()).isEqualTo(23);
                })
                .anySatisfy(metric -> {
                    assertThat(metric.path()).containsExactly("Memory");
                    assertThat(metric.value()).isInstanceOfSatisfying(CompositeData.class, memory -> {
                        assertThat(memory.get("used")).isEqualTo(100L);
                        assertThat(memory.get("committed")).isEqualTo(200L);
                        assertThat(memory.get("max")).isEqualTo(1000L);
                    });
                });
    }

    private static List<CollectedMetricGroup.Attribute> attributes(MetricsCollector collector)
    {
        return collector.collect().stream()
                .flatMap(group -> group.attributes().stream())
                .toList();
    }

    private static MetricsCollector createTestingCollector()
            throws Exception
    {
        MBeanServer mbeanServer = MBeanServerFactory.newMBeanServer();
        MBeanExporter mbeanExporter = new MBeanExporter(mbeanServer);

        ManagedMetrics managedMetrics = new ManagedMetrics();
        managedMetrics.getRequests().update(11);
        managedMetrics.getLatency().add(100);
        managedMetrics.getLatency().add(200);
        managedMetrics.getRequestTime().addNanos(100);
        managedMetrics.getRequestTime().addNanos(200);
        managedMetrics.getQueuedRequests().add(5);
        managedMetrics.getQueuedRequests().add(10);
        managedMetrics.getReadBytes().add(100);
        managedMetrics.getReadBytes().add(200);
        managedMetrics.forceLatencyMerge();
        mbeanExporter.export(new ObjectName("io.airlift.metrics.test:name=managed"), managedMetrics);

        ObjectName configuredObjectName = new ObjectName("io.airlift.metrics.test:name=configured,type=Test");
        mbeanServer.registerMBean(new StandardMBean(new ConfiguredMetrics(), ConfiguredMetricsMBean.class), configuredObjectName);

        MetricsConfig metricsConfig = new MetricsConfig().setJmxObjectNames(List.of(configuredObjectName));
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

        return new MetricsCollector(mbeanServer, mbeanExporter, metricsConfig, nodeInfo);
    }

    public static class ManagedMetrics
    {
        private final NestedMetrics flattenedStats = new NestedMetrics(31);
        private final NestedMetrics nestedStats = new NestedMetrics(41);
        private final CounterStat requests = new CounterStat();
        private final TestingTicker ticker = new TestingTicker();
        private final TimeDistribution latency = new TimeDistribution(ticker);
        private final TimeStat requestTime = new TimeStat(ticker);
        private final Distribution queuedRequests = new Distribution();
        private final DistributionStat readBytes = new DistributionStat();

        @Managed(description = "numeric gauge")
        public int getNumericGauge()
        {
            return 7;
        }

        @Flatten
        @Managed
        public NestedMetrics getFlattenedStats()
        {
            return flattenedStats;
        }

        @Managed
        @Nested
        public NestedMetrics getNestedStats()
        {
            return nestedStats;
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

        @Managed(description = "request time")
        @Nested
        public TimeStat getRequestTime()
        {
            return requestTime;
        }

        @Managed(description = "queued requests")
        @Nested
        public Distribution getQueuedRequests()
        {
            return queuedRequests;
        }

        @Managed(description = "read bytes")
        @Nested
        public DistributionStat getReadBytes()
        {
            return readBytes;
        }

        private void forceLatencyMerge()
        {
            ticker.increment(1, SECONDS);
            latency.getCount();
            requestTime.getAllTime().getCount();
        }
    }

    public static class NestedMetrics
    {
        private final int count;

        public NestedMetrics(int count)
        {
            this.count = count;
        }

        @Managed
        public int getCount()
        {
            return count;
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

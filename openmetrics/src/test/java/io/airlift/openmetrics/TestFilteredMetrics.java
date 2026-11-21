package io.airlift.openmetrics;

import io.airlift.metrics.MetricsCollector;
import io.airlift.metrics.MetricsConfig;
import io.airlift.node.NodeInfo;
import io.airlift.openmetrics.types.Metric;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestFilteredMetrics
{
    @Test
    public void testFilteredCollectionSkipsUnrequestedValues()
            throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        MBeanExporter exporter = new MBeanExporter(server);
        ManagedMetrics managed = new ManagedMetrics();
        exporter.export(new ObjectName("test:name=managed"), managed);
        ConfiguredMetrics configured = new ConfiguredMetrics();
        ObjectName configuredName = new ObjectName("test:name=configured");
        server.registerMBean(new StandardMBean(configured, ConfiguredMetricsMBean.class), configuredName);
        OpenMetricsCollector collector = new OpenMetricsCollector(new MetricsCollector(
                server,
                exporter,
                new MetricsConfig().setJmxObjectNames(List.of(configuredName)),
                new NodeInfo("test")));

        List<Metric> all = collector.collect();
        for (String name : List.of(
                "test_name_managed_Value",
                "test_name_managed_Count",
                "test_name_managed_Nested_Count",
                "test_name_managed_Sizes",
                "test_name_managed_Sizes_AllTime",
                "JMX_test_NAME_configured_ATTRIBUTE_Count",
                "JMX_test_NAME_configured_ATTRIBUTE_Memory_used",
                "missing")) {
            managed.unusedReads = 0;
            managed.distribution.snapshots = 0;
            configured.unusedReads = 0;
            List<Metric> expected = OpenMetricsCollector.filterMetrics(all, Set.of(name));
            assertThat(collector.collect(List.of(name))).isEqualTo(expected);
            assertThat(collector.collectPrometheusProtobuf(List.of(name))).isEqualTo(expected);
            assertThat(managed.unusedReads).isZero();
            assertThat(managed.distribution.snapshots).isZero();
            assertThat(configured.unusedReads).isZero();
        }
        assertThat(collector.collect(List.of())).isEqualTo(all);
        assertThat(collector.collect(null)).isEqualTo(all);
    }

    @Test
    public void testFilteredCollectionWithUnnameableSource()
            throws Exception
    {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        MBeanExporter exporter = new MBeanExporter(server);
        ObjectName name = new ObjectName("test_domain:name=status");
        server.registerMBean(new StandardMBean(new StatusBean(), StatusMBean.class), name);
        OpenMetricsCollector collector = new OpenMetricsCollector(new MetricsCollector(
                server,
                exporter,
                new MetricsConfig().setJmxObjectNames(List.of(name)),
                new NodeInfo("test")));

        assertThat(collector.collect(List.of("missing"))).isEqualTo(OpenMetricsCollector.filterMetrics(collector.collect(), Set.of("missing")));
    }

    public interface StatusMBean
    {
        String getStatus();
    }

    public static class StatusBean
            implements StatusMBean
    {
        @Override
        public String getStatus()
        {
            return "ok";
        }
    }

    public static class ManagedMetrics
    {
        private int unusedReads;
        private final CountingDistribution distribution = new CountingDistribution();
        private final DistributionStat sizes = new DistributionStat();

        @Managed
        public int getValue()
        {
            return 7;
        }

        @Managed
        public int getUnused()
        {
            unusedReads++;
            return 0;
        }

        @Managed
        @Nested
        public Distribution getDistribution()
        {
            return distribution;
        }

        @Managed
        @Nested
        public DistributionStat getSizes()
        {
            return sizes;
        }

        @Managed
        @Flatten
        public NestedMetrics getFlattened()
        {
            return new NestedMetrics();
        }

        @Managed
        @Nested
        public NestedMetrics getNested()
        {
            return new NestedMetrics();
        }
    }

    public static class NestedMetrics
    {
        @Managed
        public int getCount()
        {
            return 11;
        }
    }

    private static class CountingDistribution
            extends Distribution
    {
        private int snapshots;

        @Override
        public DistributionSnapshot snapshot()
        {
            snapshots++;
            return super.snapshot();
        }
    }

    public interface ConfiguredMetricsMBean
    {
        int getCount();

        int getUnused();

        CompositeData getMemory();
    }

    public static class ConfiguredMetrics
            implements ConfiguredMetricsMBean
    {
        private int unusedReads;

        @Override
        public int getCount()
        {
            return 13;
        }

        @Override
        public int getUnused()
        {
            unusedReads++;
            return 0;
        }

        @Override
        public CompositeData getMemory()
        {
            try {
                String[] names = {"used"};
                CompositeType type = new CompositeType("Memory", "Memory", names, names, new OpenType<?>[] {SimpleType.LONG});
                return new CompositeDataSupport(type, Map.of("used", 17L));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

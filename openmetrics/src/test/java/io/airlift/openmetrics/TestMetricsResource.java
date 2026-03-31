package io.airlift.openmetrics;

import com.google.common.collect.ImmutableMap;
import io.airlift.openmetrics.types.CompositeMetric;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import java.util.HashMap;
import java.util.Map;

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
    public void testNestedCompositeMetricExposition()
    {
        String expected =
            """
            # TYPE metric_name_count gauge
            # HELP metric_name_count metric_help
            metric_name_count 10.0
            # TYPE metric_name_nested_committed gauge
            # HELP metric_name_nested_committed metric_help
            metric_name_nested_committed 1.0
            # TYPE metric_name_nested_max gauge
            # HELP metric_name_nested_max metric_help
            metric_name_nested_max 1.0
            # TYPE metric_name_nested_used gauge
            # HELP metric_name_nested_used metric_help
            metric_name_nested_used 1.0
            """.stripIndent();

        CompositeData topLevel = createNestedCompositeData(10, 1, 1, 1);
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", topLevel, ImmutableMap.of(), "metric_help");
        assertThat(compositeMetric.getMetricExposition()).isEqualTo(expected);
    }

    private CompositeData createMemoryUsageCompositeData(long used, long committed, long max)
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

    private CompositeData createNestedCompositeData(long count, long used, long committed, long max)
    {
        try {
            CompositeData nested = createMemoryUsageCompositeData(used, committed, max);
            String[] itemNames = {"count", "nested"};
            OpenType<?>[] itemTypes = {SimpleType.LONG, nested.getCompositeType()};
            CompositeType compositeType = new CompositeType("TopLevel", "top level", itemNames, itemNames, itemTypes);
            Map<String, Object> values = new HashMap<>();
            values.put("count", count);
            values.put("nested", nested);
            return new CompositeDataSupport(compositeType, values);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

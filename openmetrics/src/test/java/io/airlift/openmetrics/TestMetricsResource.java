package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.airlift.openmetrics.MetricsResource.sanitizeMetricName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                """;

        CompositeData topLevel = createNestedCompositeData(10, 1, 1, 1);
        CompositeMetric compositeMetric = CompositeMetric.from("metric_name", topLevel, ImmutableMap.of(), "metric_help");
        assertThat(metricExpositions(ImmutableList.of(compositeMetric))).isEqualTo(expected);
        assertThat(metricExpositions(compositeMetric.subMetrics())).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    public void testMetricsWithSameNameButDifferingLabels()
    {
        Counter counterOne = new Counter("test_metric", 42, ImmutableMap.of("a", "123", "b", "456"), "Help text one");
        Gauge gauge = new Gauge("another_metric", 1, ImmutableMap.of("a", "456", "b", "789"), "Help text");
        Counter counterTwo = new Counter("test_metric", 12, ImmutableMap.of("a", "456", "b", "789"), "Help text two");

        String metricExpositions = metricExpositions(ImmutableList.of(counterOne, gauge, counterTwo));
        assertThat(metricExpositions).isEqualTo(
                """
                # TYPE test_metric counter
                # HELP test_metric Help text one
                test_metric{a="123",b="456"} 42
                test_metric{a="456",b="789"} 12
                # TYPE another_metric gauge
                # HELP another_metric Help text
                another_metric{a="456",b="789"} 1.0
                """);
    }

    @Test
    public void testCompositeMetricsWithSameNameButDifferingLabels()
    {
        CompositeData compositeDataOne = createMemoryUsageCompositeData(100L, 100L, 1000L);
        CompositeData compositeDataTwo = createMemoryUsageCompositeData(200L, 200L, 2000L);
        CompositeMetric compositeMetricOne = CompositeMetric.from("metric_name", compositeDataOne, ImmutableMap.of("a", "1"), "metric_help");
        CompositeMetric compositeMetricTwo = CompositeMetric.from("metric_name", compositeDataTwo, ImmutableMap.of("a", "2"), "metric_help");

        String metricExpositions = metricExpositions(ImmutableList.of(compositeMetricOne, compositeMetricTwo));
        assertThat(metricExpositions).isEqualTo(
                """
                # TYPE metric_name_committed gauge
                # HELP metric_name_committed metric_help
                metric_name_committed{a="1"} 100.0
                metric_name_committed{a="2"} 200.0
                # TYPE metric_name_max gauge
                # HELP metric_name_max metric_help
                metric_name_max{a="1"} 1000.0
                metric_name_max{a="2"} 2000.0
                # TYPE metric_name_used gauge
                # HELP metric_name_used metric_help
                metric_name_used{a="1"} 100.0
                metric_name_used{a="2"} 200.0
                """);
    }

    @Test
    public void testInvalidMetricFamilyPublishesAllDescriptors()
    {
        // A counter and a gauge registered with the same name but different types is invalid
        // This is unlikely to happen in practice because JMX enforces uniqueness at the metric name level
        Counter counter = new Counter("test_metric.abc", 42, ImmutableMap.of(), "Help counter");
        Gauge gauge = new Gauge("test_metric.abc", 12, ImmutableMap.of(), "Help gauge");

        assertThatThrownBy(() -> metricExpositions(ImmutableList.of(counter, gauge)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Metric family test_metric.abc contains mixed metric types: [class io.airlift.openmetrics.types.Counter, class io.airlift.openmetrics.types.Gauge]");
    }

    @Test
    public void testDuplicateMetricInFamilyPublishesSingleDescriptor()
    {
        // Note counter collides with submetric of the same name in the composite metric
        // This is unlikely to happen in practice because JMX enforces uniqueness at the metric name level
        // TODO: remove duplicate metrics with same label values, but is in hot path of metrics scraping
        Counter counter = new Counter("test_metric.abc", 42, ImmutableMap.of(), "Help text");
        Counter subMetric = new Counter("test_metric.abc", 12, ImmutableMap.of(), "Help text");
        CompositeMetric compositeMetric = new CompositeMetric("test_metric", ImmutableMap.of(), "Help text", ImmutableList.of(subMetric));

        String metricExpositions = metricExpositions(ImmutableList.of(counter, compositeMetric));
        assertThat(metricExpositions).isEqualToIgnoringWhitespace(
                """
                # TYPE test_metric.abc counter
                # HELP test_metric.abc Help text
                test_metric.abc 42
                test_metric.abc 12
                """);
    }

    private String metricExpositions(List<Metric> metrics)
    {
        StringBuilder builder = new StringBuilder();
        MetricsResource.metricExpositions(builder, metrics);
        return builder.toString();
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

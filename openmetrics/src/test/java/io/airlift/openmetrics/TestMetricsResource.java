package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static io.airlift.openmetrics.MetricsResource.managedMetricExpositions;
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

        String metricExpositions = managedMetricExpositions(Stream.of(counterOne, gauge, counterTwo));
        assertThat(metricExpositions).isEqualTo("""
            # TYPE test_metric counter
            # HELP test_metric Help text one
            test_metric{a="123",b="456"} 42
            test_metric{a="456",b="789"} 12
            # TYPE another_metric gauge
            # HELP another_metric Help text
            another_metric{a="456",b="789"} 1.0
            """.stripIndent());
    }

    @Test
    public void testInvalidMetricCollision()
    {
        // A counter and a gauge registered with the same name but different types is invalid
        // This is unlikely to happen in practice because JMX enforces uniqueness at the metric name level
        Counter counter = new Counter("test_metric.abc", 42, ImmutableMap.of(), "Help counter");
        Gauge gauge = new Gauge("test_metric.abc", 12, ImmutableMap.of(), "Help gauge");

        String metricExpositions = managedMetricExpositions(Stream.of(counter, gauge));
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

        String metricExpositions = managedMetricExpositions(Stream.of(counter, compositeMetric));
        assertThat(metricExpositions).isEqualTo("""
            # TYPE test_metric.abc counter
            # HELP test_metric.abc Help text
            test_metric.abc 42
            # TYPE test_metric.abc counter
            # HELP test_metric.abc Help text
            test_metric.abc 12
            """.stripIndent());
    }
}

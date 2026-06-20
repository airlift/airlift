package io.airlift.opentelemetry;

import io.airlift.metrics.CollectedMetricGroup;
import io.airlift.metrics.MetricSource;
import io.airlift.metrics.MetricSource.JmxMetricSource;
import io.airlift.metrics.MetricSource.ManagedMetricSource;
import io.airlift.node.NodeInfo;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.StatsBackendFactory;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.SummaryPointData;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.stats.StatsBackend.AIRLIFT;
import static io.airlift.stats.StatsBackend.OPENTELEMETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Isolated
@Execution(SAME_THREAD)
class TestOpenTelemetryMetricDataConverter
{
    private static final Resource RESOURCE = Resource.empty();
    private static final long START_EPOCH_NANOS = 1_000;
    private static final long EPOCH_NANOS = 2_000;

    @BeforeEach
    void resetBackendBeforeTest()
            throws Exception
    {
        resetStatsBackend();
        StatsBackendFactory.setBackend(AIRLIFT);
    }

    @AfterEach
    void resetBackendAfterTest()
            throws Exception
    {
        resetStatsBackend();
    }

    @Test
    void testCounterStat()
    {
        CounterStat counter = new CounterStat();
        counter.update(7);

        MetricData metric = convert("counter", counter, Map.of("node", "test"), "counter help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getType()).isEqualTo(MetricDataType.LONG_SUM);
        assertThat(metric.getName()).isEqualTo("counter");
        assertThat(metric.getDescription()).isEqualTo("counter help");
        assertThat(metric.getLongSumData().isMonotonic()).isTrue();
        assertThat(metric.getLongSumData().getAggregationTemporality()).isEqualTo(AggregationTemporality.CUMULATIVE);
        assertThat(metric.getLongSumData().getPoints()).singleElement()
                .satisfies(point -> {
                    assertThat(point.getStartEpochNanos()).isEqualTo(START_EPOCH_NANOS);
                    assertThat(point.getEpochNanos()).isEqualTo(EPOCH_NANOS);
                    assertThat(point.getValue()).isEqualTo(7);
                    assertThat(point.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("node"))).isEqualTo("test");
                });
    }

    @Test
    void testUnqualifiedObjectName()
    {
        MetricData metric = convert(
                managedSource("io.airlift.node:name=NodeInfo", NodeInfo.class),
                List.of("Environment"),
                1,
                Map.of("node", "test"),
                "node help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("io.airlift.node.NodeInfo.Environment");

        DoublePointData point = metric.getDoubleGaugeData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getAttributes().get(AttributeKey.stringKey("node"))).isEqualTo("test");
        assertThat(point.getAttributes().asMap()).doesNotContainKey(AttributeKey.stringKey("name"));
    }

    @Test
    void testQualifiedObjectName()
    {
        MetricData metric = convert(
                managedSource("io.airlift.stats:type=CounterStat,name=ForDiscoveryClient", CounterStat.class, "ForDiscoveryClient"),
                List.of("RequestStats", "AllResponse"),
                1,
                Map.of("node", "test"),
                "client help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("io.airlift.stats.CounterStat.RequestStats.AllResponse");

        DoublePointData point = metric.getDoubleGaugeData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getAttributes().get(AttributeKey.stringKey("name"))).isEqualTo("ForDiscoveryClient");
        assertThat(point.getAttributes().get(AttributeKey.stringKey("node"))).isEqualTo("test");
        assertThat(point.getAttributes().asMap()).doesNotContainKey(AttributeKey.stringKey("type"));
    }

    @Test
    void testConnectorObjectName()
    {
        MetricData metric = convert(
                managedSource("trino.plugin.hive:type=Distribution,name=hive,catalog=hive", Distribution.class, "hive"),
                List.of("QueuedSplits"),
                1,
                Map.of(),
                "connector help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("io.airlift.stats.Distribution.QueuedSplits");

        DoublePointData point = metric.getDoubleGaugeData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getAttributes().get(AttributeKey.stringKey("catalog"))).isEqualTo("hive");
        assertThat(point.getAttributes().get(AttributeKey.stringKey("name"))).isEqualTo("hive");
        assertThat(point.getAttributes().asMap()).doesNotContainKey(AttributeKey.stringKey("type"));
    }

    @Test
    void testManagedObjectNameWithoutTypeOrNameUsesExportedType()
    {
        MetricData metric = convert(
                managedSource("trino.execution.scheduler:segment=foo", TimeStat.class, Map.of("segment", "foo")),
                List.of("PlacementFailures"),
                1,
                Map.of(),
                "scheduler help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("io.airlift.stats.TimeStat.PlacementFailures");

        DoublePointData point = metric.getDoubleGaugeData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getAttributes().get(AttributeKey.stringKey("segment"))).isEqualTo("foo");
    }

    @Test
    void testJmxObjectNameWithoutTypeOrNameFallsBackToDomain()
            throws MalformedObjectNameException
    {
        MetricData metric = convert(
                new JmxMetricSource(new ObjectName("trino.execution.scheduler:segment=foo")),
                List.of("PlacementFailures"),
                1,
                Map.of(),
                "scheduler help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("trino.execution.scheduler.PlacementFailures");

        DoublePointData point = metric.getDoubleGaugeData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getAttributes().get(AttributeKey.stringKey("segment"))).isEqualTo("foo");
    }

    @Test
    void testDistributionWithExponentialHistogram()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        Distribution distribution = new Distribution();
        distribution.add(10);
        distribution.add(20, 2);

        MetricData metric = convert("distribution", distribution, Map.of(), "distribution help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM);
        assertThat(metric.getName()).isEqualTo("distribution");
        assertThat(metric.getDescription()).isEqualTo("distribution help");
        assertThat(metric.getUnit()).isEmpty();
        assertThat(metric.getExponentialHistogramData().getAggregationTemporality()).isEqualTo(AggregationTemporality.CUMULATIVE);

        ExponentialHistogramPointData point = metric.getExponentialHistogramData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getStartEpochNanos()).isEqualTo(START_EPOCH_NANOS);
        assertThat(point.getEpochNanos()).isEqualTo(EPOCH_NANOS);
        assertThat(point.getCount()).isEqualTo(3);
        assertThat(point.getSum()).isEqualTo(50);
        assertThat(point.hasMin()).isTrue();
        assertThat(point.getMin()).isEqualTo(10);
        assertThat(point.hasMax()).isTrue();
        assertThat(point.getMax()).isEqualTo(20);
        assertThat(point.getPositiveBuckets().getTotalCount()).isEqualTo(3);
    }

    @Test
    void testTimeStatWithExponentialHistogram()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        TimeStat timeStat = new TimeStat();
        timeStat.addNanos(100);
        timeStat.addNanos(200);

        MetricData metric = convert("timer", timeStat, Map.of(), "timer help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM);
        assertThat(metric.getName()).isEqualTo("timer");
        assertThat(metric.getUnit()).isEqualTo("ns");

        ExponentialHistogramPointData point = metric.getExponentialHistogramData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getCount()).isEqualTo(2);
        assertThat(point.getSum()).isEqualTo(300);
        assertThat(point.getMin()).isEqualTo(100);
        assertThat(point.getMax()).isEqualTo(200);
    }

    @Test
    void testDistributionStatWithExponentialHistogram()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        DistributionStat distributionStat = new DistributionStat();
        distributionStat.add(10);
        distributionStat.add(20);

        MetricData metric = convert("distribution", distributionStat, Map.of(), "distribution help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM);
        assertThat(metric.getName()).isEqualTo("distribution");

        ExponentialHistogramPointData point = metric.getExponentialHistogramData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getCount()).isEqualTo(2);
        assertThat(point.getSum()).isEqualTo(30);
    }

    @Test
    void testTimeStatWithSummaryFallback()
    {
        StatsBackendFactory.setBackend(AIRLIFT);
        TimeStat timeStat = new TimeStat();
        timeStat.addNanos(100);
        timeStat.addNanos(200);

        List<MetricData> metrics = convert("timer", timeStat, Map.of(), "timer help");

        assertThat(metrics)
                .extracting(MetricData::getName)
                .containsExactly(
                        "timer.AllTime",
                        "timer.FifteenMinutes",
                        "timer.FiveMinutes",
                        "timer.OneMinute");
        assertThat(metrics)
                .allSatisfy(metric -> assertThat(metric.getType()).isEqualTo(MetricDataType.SUMMARY));
        assertThat(metrics)
                .allSatisfy(metric -> assertThat(metric.getUnit()).isEqualTo("s"));

        SummaryPointData allTime = metrics.stream()
                .filter(metric -> metric.getName().equals("timer.AllTime"))
                .collect(onlyElement())
                .getSummaryData()
                .getPoints()
                .stream()
                .collect(onlyElement());
        assertThat(allTime.getCount()).isEqualTo(2);
        assertThat(allTime.getSum()).isEqualTo(3.0e-7);
    }

    @Test
    void testTimeDistributionSummaryFallbackUnit()
    {
        StatsBackendFactory.setBackend(AIRLIFT);
        TimeDistribution distribution = new TimeDistribution(TimeUnit.MILLISECONDS);
        distribution.add(TimeUnit.MILLISECONDS.toNanos(10));
        distribution.add(TimeUnit.MILLISECONDS.toNanos(20));

        MetricData metric = convert("timer", distribution, Map.of(), "timer help")
                .stream()
                .collect(onlyElement());

        assertThat(metric.getType()).isEqualTo(MetricDataType.SUMMARY);
        assertThat(metric.getUnit()).isEqualTo("ms");

        SummaryPointData point = metric.getSummaryData().getPoints().stream()
                .collect(onlyElement());
        assertThat(point.getCount()).isEqualTo(2);
        assertThat(point.getSum()).isEqualTo(30);
    }

    @Test
    void testDroppedPointsAreDeterministic()
    {
        OpenTelemetryMetricDataConverter converter = new OpenTelemetryMetricDataConverter(2, 100);

        List<MetricData> forwardOrder = converter.convertWithDroppedPoints(
                        List.of(
                                metricGroup("metric", "c", 3),
                                metricGroup("metric", "a", 1),
                                metricGroup("metric", "b", 2)),
                        RESOURCE,
                        START_EPOCH_NANOS,
                        EPOCH_NANOS)
                .metricData();
        List<MetricData> reverseOrder = converter.convertWithDroppedPoints(
                        List.of(
                                metricGroup("metric", "b", 2),
                                metricGroup("metric", "a", 1),
                                metricGroup("metric", "c", 3)),
                        RESOURCE,
                        START_EPOCH_NANOS,
                        EPOCH_NANOS)
                .metricData();

        assertThat(pointIds(forwardOrder)).containsExactly("a", "b");
        assertThat(pointIds(reverseOrder)).containsExactly("a", "b");
    }

    private static List<MetricData> convert(String metricName, Object value, Map<String, String> labels, String description)
    {
        return convert(new ManagedMetricSource(metricName), List.of(), value, labels, description);
    }

    private static List<MetricData> convert(MetricSource source, List<String> path, Object value, Map<String, String> labels, String description)
    {
        CollectedMetricGroup metricGroup = new CollectedMetricGroup(
                source,
                labels,
                List.of(new CollectedMetricGroup.Attribute(path, value, description)));
        return new OpenTelemetryMetricDataConverter()
                .convertWithDroppedPoints(List.of(metricGroup), RESOURCE, START_EPOCH_NANOS, EPOCH_NANOS)
                .metricData();
    }

    private static CollectedMetricGroup metricGroup(String metricName, String id, double value)
    {
        return new CollectedMetricGroup(
                new ManagedMetricSource(metricName),
                Map.of("id", id),
                List.of(new CollectedMetricGroup.Attribute(List.of(), value, "help")));
    }

    private static List<String> pointIds(List<MetricData> metrics)
    {
        return metrics.stream()
                .collect(onlyElement())
                .getDoubleGaugeData()
                .getPoints()
                .stream()
                .map(point -> point.getAttributes().get(AttributeKey.stringKey("id")))
                .toList();
    }

    private static ManagedMetricSource managedSource(String objectName, Class<?> exportedType)
    {
        return new ManagedMetricSource(objectName, Optional.of(exportedType), Optional.empty(), Map.of());
    }

    private static ManagedMetricSource managedSource(String objectName, Class<?> exportedType, String originalName)
    {
        return new ManagedMetricSource(objectName, Optional.of(exportedType), Optional.of(originalName), Map.of());
    }

    private static ManagedMetricSource managedSource(String objectName, Class<?> exportedType, Map<String, String> originalProperties)
    {
        return new ManagedMetricSource(objectName, Optional.of(exportedType), Optional.empty(), originalProperties);
    }

    private static void resetStatsBackend()
            throws Exception
    {
        Method method = StatsBackendFactory.class.getDeclaredMethod("resetForTesting");
        method.setAccessible(true);
        try {
            method.invoke(null);
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throwIfUnchecked(cause);
            throw e;
        }
    }
}

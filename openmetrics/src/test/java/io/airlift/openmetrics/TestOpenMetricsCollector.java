package io.airlift.openmetrics;

import com.google.common.collect.ImmutableMap;
import io.airlift.metrics.CollectedMetricGroup;
import io.airlift.metrics.CollectedMetricGroup.Attribute;
import io.airlift.metrics.MetricSource.JmxMetricSource;
import io.airlift.metrics.MetricSource.ManagedMetricSource;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class TestOpenMetricsCollector
{
    private static final Map<String, String> LABELS = ImmutableMap.of("region", "b", "team", "a");

    @Test
    public void testConvertGroupNames()
            throws Exception
    {
        ObjectName objectName = new ObjectName("io.airlift.metrics.test:name=configured,type=Test");

        assertThat(OpenMetricsCollector.toOpenMetrics(List.of(
                new CollectedMetricGroup(
                        new ManagedMetricSource("io.airlift.metrics.test:name=managed"),
                        LABELS,
                        List.of(new Attribute(List.of("NumericGauge"), 7, "metric help"))),
                new CollectedMetricGroup(
                        new JmxMetricSource(objectName),
                        LABELS,
                        List.of(new Attribute(List.of("Count"), 23, "metric help"))))))
                .extracting(Metric::metricName)
                .containsExactly(
                        "io_airlift_metrics_test_name_managed_NumericGauge",
                        "JMX_io_airlift_metrics_test_NAME_configured_TYPE_Test_ATTRIBUTE_Count");
    }

    @Test
    public void testConvertNumberToGauge()
    {
        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), 7, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(Gauge.class, gauge -> {
            assertThat(gauge.metricName()).isEqualTo("metric_name");
            assertThat(gauge.value()).isEqualTo(7.0);
            assertThat(gauge.labels()).isEqualTo(LABELS);
            assertThat(gauge.help()).isEqualTo("metric help");
        });
    }

    @Test
    public void testConvertBooleanToGauge()
    {
        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), true, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(Gauge.class, gauge -> {
            assertThat(gauge.metricName()).isEqualTo("metric_name");
            assertThat(gauge.value()).isEqualTo(1.0);
            assertThat(gauge.labels()).isEqualTo(LABELS);
            assertThat(gauge.help()).isEqualTo("metric help");
        });
    }

    @Test
    public void testConvertCompositeDataToCompositeMetric()
    {
        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), createMemoryUsageCompositeData(100, 200, 1000), "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(CompositeMetric.class, compositeMetric -> {
            assertThat(compositeMetric.metricName()).isEqualTo("metric_name");
            assertThat(compositeMetric.labels()).isEqualTo(LABELS);
            assertThat(compositeMetric.help()).isEqualTo("metric help");
            assertThat(compositeMetric.subMetrics())
                    .filteredOn(Gauge.class::isInstance)
                    .map(Gauge.class::cast)
                    .extracting(Gauge::metricName, Gauge::value)
                    .contains(
                            tuple("metric_name_committed", 200.0),
                            tuple("metric_name_max", 1000.0),
                            tuple("metric_name_used", 100.0));
        });
    }

    @Test
    public void testConvertTabularDataToCompositeMetric()
    {
        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), createTestTabularData(), "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(CompositeMetric.class, compositeMetric -> {
            assertThat(compositeMetric.metricName()).isEqualTo("metric_name");
            assertThat(compositeMetric.labels()).isEqualTo(LABELS);
            assertThat(compositeMetric.help()).isEqualTo("metric help");
            assertThat(compositeMetric.subMetrics())
                    .filteredOn(Gauge.class::isInstance)
                    .map(Gauge.class::cast)
                    .extracting(Gauge::metricName, Gauge::value, Gauge::labels)
                    .containsExactlyInAnyOrder(
                            tuple("metric_name_value", 1.0, ImmutableMap.<String, String>builder()
                                    .putAll(LABELS)
                                    .put("name", "one")
                                    .buildOrThrow()),
                            tuple("metric_name_value", 2.0, ImmutableMap.<String, String>builder()
                                    .putAll(LABELS)
                                    .put("name", "two")
                                    .buildOrThrow()));
        });
    }

    @Test
    public void testConvertEmptyTabularDataToEmptyCompositeMetric()
    {
        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), createEmptyTestTabularData(), "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(CompositeMetric.class, compositeMetric -> assertThat(compositeMetric.subMetrics()).isEmpty());
    }

    @Test
    public void testConvertCounterStatToCounter()
    {
        CounterStat counterStat = new CounterStat();
        counterStat.update(11);

        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), counterStat, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(Counter.class, counter -> {
            assertThat(counter.metricName()).isEqualTo("metric_name");
            assertThat(counter.value()).isEqualTo(11);
            assertThat(counter.labels()).isEqualTo(LABELS);
            assertThat(counter.help()).isEqualTo("metric help");
        });
    }

    @Test
    public void testConvertTimeDistributionToSummary()
    {
        TestingTicker ticker = new TestingTicker();
        TimeDistribution timeDistribution = new TimeDistribution(ticker);
        timeDistribution.add(100);
        timeDistribution.add(200);
        ticker.increment(1, SECONDS);
        timeDistribution.getCount();

        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), timeDistribution, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(Summary.class, summary -> {
            assertThat(summary.metricName()).isEqualTo("metric_name");
            assertThat(summary.count()).isEqualTo(2);
            assertThat(summary.labels()).isEqualTo(LABELS);
            assertThat(summary.help()).isEqualTo("metric help");
        });
    }

    @Test
    public void testConvertDistributionToSummary()
    {
        Distribution distribution = new Distribution();
        distribution.add(100);
        distribution.add(200);

        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), distribution, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(Summary.class, summary -> {
            assertThat(summary.metricName()).isEqualTo("metric_name");
            assertThat(summary.count()).isEqualTo(2);
            assertThat(summary.sum()).isEqualTo(300);
            assertThat(summary.quantiles()).containsKeys(0.01, 0.5, 0.99);
            assertThat(summary.labels()).isEqualTo(LABELS);
            assertThat(summary.help()).isEqualTo("metric help");
        });
    }

    @Test
    public void testConvertTimeStatToSummaries()
    {
        TestingTicker ticker = new TestingTicker();
        TimeStat timeStat = new TimeStat(ticker);
        timeStat.addNanos(100);
        timeStat.addNanos(200);
        ticker.increment(1, SECONDS);

        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), timeStat, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(CompositeMetric.class, compositeMetric -> {
            assertThat(compositeMetric.subMetrics())
                    .filteredOn(Summary.class::isInstance)
                    .map(Summary.class::cast)
                    .extracting(Summary::metricName, Summary::count)
                    .containsExactlyInAnyOrder(
                            tuple("metric_name_OneMinute", 2L),
                            tuple("metric_name_FiveMinutes", 2L),
                            tuple("metric_name_FifteenMinutes", 2L),
                            tuple("metric_name_AllTime", 2L));
        });
    }

    @Test
    public void testConvertDistributionStatToSummaries()
    {
        DistributionStat distributionStat = new DistributionStat();
        distributionStat.add(100);
        distributionStat.add(200);

        Optional<Metric> metric = OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), distributionStat, "metric help"), LABELS);

        assertThat(metric).isPresent();
        assertThat(metric.orElseThrow()).isInstanceOfSatisfying(CompositeMetric.class, compositeMetric -> {
            assertThat(compositeMetric.subMetrics())
                    .filteredOn(Summary.class::isInstance)
                    .map(Summary.class::cast)
                    .extracting(Summary::metricName, Summary::count)
                    .containsExactlyInAnyOrder(
                            tuple("metric_name_OneMinute", 2L),
                            tuple("metric_name_FiveMinutes", 2L),
                            tuple("metric_name_FifteenMinutes", 2L),
                            tuple("metric_name_AllTime", 2L));
        });
    }

    @Test
    public void testFilterCompositeSubMetric()
    {
        CompositeMetric compositeMetric = new CompositeMetric(
                "metric_name",
                LABELS,
                "metric help",
                List.of(
                        new Summary("metric_name_OneMinute", 1L, 1.0, null, Map.of(), LABELS, "metric help"),
                        new Summary("metric_name_AllTime", 2L, 2.0, null, Map.of(), LABELS, "metric help")));

        assertThat(OpenMetricsCollector.filterMetrics(List.of(compositeMetric), Set.of("metric_name_AllTime")))
                .singleElement()
                .isInstanceOfSatisfying(CompositeMetric.class, filteredMetric -> assertThat(filteredMetric.subMetrics())
                        .singleElement()
                        .isInstanceOfSatisfying(Summary.class, summary -> assertThat(summary.metricName()).isEqualTo("metric_name_AllTime")));
    }

    @Test
    public void testFindCompositeMetricRoot()
    {
        CompositeMetric compositeMetric = new CompositeMetric(
                "metric_name",
                LABELS,
                "metric help",
                List.of(
                        new Summary("metric_name_OneMinute", 1L, 1.0, null, Map.of(), LABELS, "metric help"),
                        new Summary("metric_name_AllTime", 2L, 2.0, null, Map.of(), LABELS, "metric help")));

        assertThat(OpenMetricsCollector.findMetric(List.of(compositeMetric), "metric_name"))
                .containsSame(compositeMetric);
        assertThat(OpenMetricsCollector.findMetric(List.of(compositeMetric), "metric_name_AllTime"))
                .containsInstanceOf(Summary.class);
    }

    @Test
    public void testIgnoreUnsupportedValue()
    {
        assertThat(OpenMetricsCollector.toOpenMetric(new Attribute(List.of("metric_name"), "ignored", "metric help"), LABELS)).isEmpty();
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

    private static TabularData createTestTabularData()
    {
        try {
            TabularDataSupport tabularData = createEmptyTestTabularData();
            CompositeType compositeType = tabularData.getTabularType().getRowType();

            tabularData.put(new CompositeDataSupport(compositeType, ImmutableMap.of("name", "one", "value", 1L)));
            tabularData.put(new CompositeDataSupport(compositeType, ImmutableMap.of("name", "two", "value", 2L)));

            return tabularData;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TabularDataSupport createEmptyTestTabularData()
    {
        try {
            String[] itemNames = {"name", "value"};
            OpenType<?>[] itemTypes = {SimpleType.STRING, SimpleType.LONG};
            CompositeType compositeType = new CompositeType("TestData", "Test Data", itemNames, itemNames, itemTypes);
            return new TabularDataSupport(new TabularType("TestTable", "Test Table", compositeType, new String[] {"name"}));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

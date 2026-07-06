package io.airlift.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.airlift.metrics.CollectedMetricGroup;
import io.airlift.metrics.CollectedMetricGroup.Attribute;
import io.airlift.metrics.CompositeDataFlattener;
import io.airlift.metrics.MetricSource;
import io.airlift.metrics.MetricSource.ManagedMetricSource;
import io.airlift.metrics.StatWindows;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.Distribution.DistributionSnapshot;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeDistribution.TimeDistributionSnapshot;
import io.airlift.stats.TimeStat;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.GaugeData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.metrics.data.SummaryData;
import io.opentelemetry.sdk.metrics.data.SummaryPointData;
import io.opentelemetry.sdk.metrics.data.ValueAtQuantile;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

public class OpenTelemetryMetricDataConverter
{
    private static final int DEFAULT_MAX_POINTS_PER_METRIC_FAMILY = 2_000;
    private static final int DEFAULT_MAX_POINTS_PER_SCRAPE = 100_000;
    private static final Logger log = Logger.get(OpenTelemetryMetricDataConverter.class);
    private static final String NANOSECOND_UNIT = "ns";
    private static final Comparator<MetricPoint> METRIC_POINT_ORDERING = Comparator
            .comparing((MetricPoint metricPoint) -> attributesComparisonKey(metricPoint.attributes()))
            .thenComparing(metricPoint -> String.valueOf(metricPoint.description()))
            .thenComparing(metricPoint -> String.valueOf(metricPoint.value()));
    private static final CharMatcher NON_ALLOWED_METRIC_NAME_CHARACTERS = CharMatcher
            .inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("._-"))
            .negate()
            .precomputed();

    private final int maxPointsPerMetricFamily;
    private final int maxPointsPerScrape;

    public OpenTelemetryMetricDataConverter()
    {
        this(DEFAULT_MAX_POINTS_PER_METRIC_FAMILY, DEFAULT_MAX_POINTS_PER_SCRAPE);
    }

    @VisibleForTesting
    OpenTelemetryMetricDataConverter(int maxPointsPerMetricFamily, int maxPointsPerScrape)
    {
        checkArgument(maxPointsPerMetricFamily > 0, "maxPointsPerMetricFamily must be positive");
        checkArgument(maxPointsPerScrape > 0, "maxPointsPerScrape must be positive");
        this.maxPointsPerMetricFamily = maxPointsPerMetricFamily;
        this.maxPointsPerScrape = maxPointsPerScrape;
    }

    ConversionResult convertWithDroppedPoints(Collection<CollectedMetricGroup> metricGroups, Resource resource, long startEpochNanos, long epochNanos)
    {
        Map<MetricFamilyKey, List<MetricPoint>> metricFamilies = metricGroups.stream()
                .flatMap(OpenTelemetryMetricDataConverter::toMetricPoints)
                .collect(groupingBy(MetricPoint::family));

        List<Map.Entry<MetricFamilyKey, List<MetricPoint>>> sortedMetricFamilies = metricFamilies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(MetricFamilyKey::name)
                        .thenComparing(MetricFamilyKey::kind)
                        .thenComparing(MetricFamilyKey::unit)))
                .toList();
        ImmutableList.Builder<MetricData> metricData = ImmutableList.builderWithExpectedSize(sortedMetricFamilies.size());
        long droppedPoints = 0;
        int remainingPoints = maxPointsPerScrape;
        for (Map.Entry<MetricFamilyKey, List<MetricPoint>> entry : sortedMetricFamilies) {
            List<MetricPoint> metricFamily = entry.getValue();
            if (remainingPoints == 0) {
                droppedPoints += metricFamily.size();
                continue;
            }

            int pointLimit = Math.min(maxPointsPerMetricFamily, remainingPoints);
            int pointCount = Math.min(metricFamily.size(), pointLimit);
            List<MetricPoint> selectedMetricFamily = selectMetricPoints(metricFamily, pointCount);
            Optional<MetricData> convertedMetricData = convertMetricFamily(entry.getKey(), selectedMetricFamily, resource, startEpochNanos, epochNanos);
            if (convertedMetricData.isEmpty()) {
                continue;
            }

            metricData.add(convertedMetricData.orElseThrow());
            remainingPoints -= pointCount;
            droppedPoints += metricFamily.size() - pointCount;
        }
        return new ConversionResult(metricData.build(), droppedPoints);
    }

    private static List<MetricPoint> selectMetricPoints(List<MetricPoint> metricFamily, int pointCount)
    {
        if (pointCount == metricFamily.size()) {
            return metricFamily;
        }
        return metricFamily.stream()
                .sorted(METRIC_POINT_ORDERING)
                .limit(pointCount)
                .toList();
    }

    private static String attributesComparisonKey(Attributes attributes)
    {
        return attributes.asMap().entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<AttributeKey<?>, Object> entry) -> entry.getKey().getKey())
                        .thenComparing(entry -> String.valueOf(entry.getValue())))
                .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                .collect(joining("|"));
    }

    private static Optional<MetricData> convertMetricFamily(
            MetricFamilyKey metricFamilyKey,
            List<MetricPoint> selectedMetricFamily,
            Resource resource,
            long startEpochNanos,
            long epochNanos)
    {
        try {
            return Optional.of(toMetricData(metricFamilyKey, selectedMetricFamily, resource, startEpochNanos, epochNanos));
        }
        catch (RuntimeException e) {
            log.warn(e, "Unable to convert OpenTelemetry metric family %s", metricFamilyKey.name());
            return Optional.empty();
        }
    }

    private static MetricData toMetricData(
            MetricFamilyKey metricFamilyKey,
            List<MetricPoint> selectedMetricFamily,
            Resource resource,
            long startEpochNanos,
            long epochNanos)
    {
        MetricPoint firstMetric = selectedMetricFamily.getFirst();
        return switch (metricFamilyKey.kind()) {
            case DOUBLE_GAUGE -> toDoubleGauge(metricFamilyKey.name(), selectedMetricFamily, firstMetric, resource, startEpochNanos, epochNanos);
            case LONG_SUM -> toLongSum(metricFamilyKey.name(), selectedMetricFamily, firstMetric, resource, startEpochNanos, epochNanos);
            case SUMMARY -> toSummary(metricFamilyKey.name(), selectedMetricFamily, firstMetric, resource, startEpochNanos, epochNanos);
            case EXPONENTIAL_HISTOGRAM -> toExponentialHistogram(metricFamilyKey.name(), selectedMetricFamily, firstMetric, resource, startEpochNanos, epochNanos);
        };
    }

    private static MetricData toDoubleGauge(String metricName, List<MetricPoint> metricFamily, MetricPoint firstMetric, Resource resource, long startEpochNanos, long epochNanos)
    {
        List<DoublePointData> points = metricFamily.stream()
                .map(metric -> DoublePointData.create(startEpochNanos, epochNanos, metric.attributes(), (double) metric.value(), List.of()))
                .collect(toImmutableList());
        return createMetricData(ImmutableMetricData::createDoubleGauge, metricName, firstMetric, resource, GaugeData.createDoubleGaugeData(points));
    }

    private static MetricData toLongSum(String metricName, List<MetricPoint> metricFamily, MetricPoint firstMetric, Resource resource, long startEpochNanos, long epochNanos)
    {
        List<LongPointData> points = metricFamily.stream()
                .map(metric -> LongPointData.create(startEpochNanos, epochNanos, metric.attributes(), (long) metric.value()))
                .collect(toImmutableList());
        return createMetricData(ImmutableMetricData::createLongSum, metricName, firstMetric, resource, SumData.createLongSumData(true, AggregationTemporality.CUMULATIVE, points));
    }

    private static MetricData toSummary(String metricName, List<MetricPoint> metricFamily, MetricPoint firstMetric, Resource resource, long startEpochNanos, long epochNanos)
    {
        List<SummaryPointData> points = metricFamily.stream()
                .map(metric -> {
                    SummaryValue summary = (SummaryValue) metric.value();
                    return SummaryPointData.create(
                            startEpochNanos,
                            epochNanos,
                            metric.attributes(),
                            summary.count(),
                            summary.sum(),
                            summary.valuesAtQuantiles());
                })
                .collect(toImmutableList());
        return createMetricData(ImmutableMetricData::createDoubleSummary, metricName, firstMetric, resource, SummaryData.create(points));
    }

    private static MetricData toExponentialHistogram(String metricName, List<MetricPoint> metricFamily, MetricPoint firstMetric, Resource resource, long startEpochNanos, long epochNanos)
    {
        List<ExponentialHistogramPointData> points = metricFamily.stream()
                .map(metric -> toExponentialHistogramPoint((ExponentialHistogramSnapshot) metric.value(), metric.attributes(), startEpochNanos, epochNanos))
                .collect(toImmutableList());
        return createMetricData(ImmutableMetricData::createExponentialHistogram, metricName, firstMetric, resource, ExponentialHistogramData.create(AggregationTemporality.CUMULATIVE, points));
    }

    private static <T> MetricData createMetricData(MetricDataFactory<T> factory, String metricName, MetricPoint firstMetric, Resource resource, T data)
    {
        return factory.create(resource, OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE, metricName, firstMetric.description(), firstMetric.unit(), data);
    }

    private static ExponentialHistogramPointData toExponentialHistogramPoint(
            ExponentialHistogramSnapshot snapshot,
            Attributes attributes,
            long startEpochNanos,
            long epochNanos)
    {
        boolean hasMinMax = snapshot.count() > 0;
        return ExponentialHistogramPointData.create(
                snapshot.scale(),
                snapshot.sum(),
                snapshot.zeroCount(),
                hasMinMax,
                hasMinMax ? snapshot.min() : 0,
                hasMinMax,
                hasMinMax ? snapshot.max() : 0,
                toExponentialHistogramBuckets(snapshot.scale(), snapshot.positiveBuckets()),
                toExponentialHistogramBuckets(snapshot.scale(), snapshot.negativeBuckets()),
                startEpochNanos,
                epochNanos,
                attributes,
                List.of());
    }

    private static ExponentialHistogramBuckets toExponentialHistogramBuckets(int scale, Buckets buckets)
    {
        return ExponentialHistogramBuckets.create(
                scale,
                buckets.offset(),
                Arrays.stream(buckets.counts())
                        .boxed()
                        .collect(toImmutableList()));
    }

    private static Stream<MetricPoint> toMetricPoints(CollectedMetricGroup metricGroup)
    {
        return metricGroup.attributes().stream()
                .flatMap(attribute -> {
                    MetricIdentity metricIdentity = metricIdentity(metricGroup.source(), attribute);
                    return toMetricPoints(metricIdentity.name(), attribute, attributes(metricGroup.labels(), metricIdentity.labels()));
                });
    }

    private static MetricIdentity metricIdentity(MetricSource source, Attribute attribute)
    {
        return switch (source) {
            case MetricSource.JmxMetricSource jmxMetricSource -> metricIdentity(jmxMetricSource.name(), attribute);
            case MetricSource.ManagedMetricSource managedMetricSource -> metricIdentity(managedMetricSource, attribute);
        };
    }

    private static MetricIdentity metricIdentity(ManagedMetricSource source, Attribute attribute)
    {
        Optional<ObjectName> objectName = parseObjectName(source.name());
        if (source.exportedType().isPresent()) {
            Class<?> exportedType = source.exportedType().orElseThrow();
            String baseName = exportedType.getName();
            return new MetricIdentity(metricName(baseName, attribute), managedMetricLabels(source, objectName, exportedType.getSimpleName()));
        }

        return objectName
                .map(name -> metricIdentity(name, attribute))
                .orElseGet(() -> new MetricIdentity(metricName(source.name(), attribute), Map.of()));
    }

    private static Map<String, String> managedMetricLabels(ManagedMetricSource source, Optional<ObjectName> objectName, String typeName)
    {
        Map<String, String> labels = objectName
                .<Map<String, String>>map(name -> new LinkedHashMap<>(name.getKeyPropertyList()))
                .orElseGet(LinkedHashMap::new);

        // The final ObjectName properties are the source of labels because ObjectNameGenerators can
        // add identity such as Trino connector catalogs. The original export inputs only tell us
        // which final properties are object-kind metadata already represented by the metric name.
        labels.remove("type", typeName);
        if (isObjectKindName(source, labels.get("name"), typeName)) {
            labels.remove("name");
        }
        return labels;
    }

    private static boolean isObjectKindName(ManagedMetricSource source, String name, String typeName)
    {
        if (!typeName.equals(name)) {
            return false;
        }

        // An explicit generated name is identity, even when it happens to equal the type name. The
        // default unqualified export and property-map exports that set name=<type> are object-kind
        // metadata and should not become labels.
        return source.originalName().isEmpty() &&
                (source.originalProperties().isEmpty() || typeName.equals(source.originalProperties().get("name")));
    }

    private static Optional<ObjectName> parseObjectName(String name)
    {
        try {
            return Optional.of(new ObjectName(name));
        }
        catch (MalformedObjectNameException e) {
            return Optional.empty();
        }
    }

    private static MetricIdentity metricIdentity(ObjectName objectName, Attribute attribute)
    {
        Map<String, String> labels = new LinkedHashMap<>(objectName.getKeyPropertyList());

        String baseName = objectName.getKeyProperty("type");
        if (baseName != null) {
            labels.remove("type");
            return new MetricIdentity(metricName(baseName, attribute), labels);
        }

        baseName = objectName.getKeyProperty("name");
        if (baseName != null) {
            labels.remove("name");
            return new MetricIdentity(metricName(baseName, attribute), labels);
        }

        return new MetricIdentity(metricName(objectName.getDomain(), attribute), labels);
    }

    private static String metricName(String baseName, Attribute attribute)
    {
        String attributeName = sanitizeMetricName(String.join(".", attribute.path()));
        String metricName = sanitizeMetricName(baseName);
        if (attributeName.isEmpty()) {
            return metricName;
        }
        if (metricName.isEmpty()) {
            return attributeName;
        }
        return metricName + "." + attributeName;
    }

    private static String sanitizeMetricName(String name)
    {
        return NON_ALLOWED_METRIC_NAME_CHARACTERS.collapseFrom(name, '_');
    }

    private static Stream<MetricPoint> toMetricPoints(String metricName, Attribute attribute, Attributes attributes)
    {
        return toMetricPoints(metricName, attribute.value(), attributes, attribute.description());
    }

    private static Stream<MetricPoint> toMetricPoints(String metricName, Object value, Attributes attributes, String description)
    {
        return switch (value) {
            case Number number -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.DOUBLE_GAUGE, ""), number.doubleValue(), attributes, description));
            case Boolean bool -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.DOUBLE_GAUGE, ""), bool ? 1.0 : 0.0, attributes, description));
            case CompositeData compositeData -> flattenedDataPoints(metricName, compositeData, attributes, description);
            case TabularData tabularData -> flattenedDataPoints(metricName, tabularData, attributes, description);
            case CounterStat counterStat -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.LONG_SUM, ""), counterStat.getTotalCount(), attributes, description));
            case TimeDistribution timeDistribution -> timeDistribution.exponentialHistogramSnapshot()
                    .map(snapshot -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.EXPONENTIAL_HISTOGRAM, NANOSECOND_UNIT), snapshot, attributes, description)))
                    .orElseGet(() -> Stream.of(timeSummary(metricName, timeDistribution, attributes, description)));
            case TimeStat timeStat -> timeStat.exponentialHistogramSnapshot()
                    .map(snapshot -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.EXPONENTIAL_HISTOGRAM, NANOSECOND_UNIT), snapshot, attributes, description)))
                    .orElseGet(() -> timeStatSummaries(metricName, timeStat, attributes, description));
            case Distribution distribution -> distribution.exponentialHistogramSnapshot()
                    .map(snapshot -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.EXPONENTIAL_HISTOGRAM, ""), snapshot, attributes, description)))
                    .orElseGet(() -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.SUMMARY, ""), toSummary(distribution), attributes, description)));
            case DistributionStat distributionStat -> distributionStat.exponentialHistogramSnapshot()
                    .map(snapshot -> Stream.of(new MetricPoint(new MetricFamilyKey(metricName, MetricKind.EXPONENTIAL_HISTOGRAM, ""), snapshot, attributes, description)))
                    .orElseGet(() -> distributionStatSummaries(metricName, distributionStat, attributes, description));
            case null, default -> Stream.of();
        };
    }

    private static Stream<MetricPoint> flattenedDataPoints(String metricName, Object value, Attributes attributes, String description)
    {
        ImmutableList.Builder<MetricPoint> points = ImmutableList.builder();
        CompositeDataFlattener.flatten(metricName, value, Map.of(), ".", OpenTelemetryMetricDataConverter::sanitizeMetricName, (name, labels, leafValue) ->
                toMetricPoints(name, leafValue, rowAttributes(attributes, labels), description).forEach(points::add));
        return points.build().stream();
    }

    private static Attributes rowAttributes(Attributes attributes, Map<String, String> labels)
    {
        if (labels.isEmpty()) {
            return attributes;
        }
        AttributesBuilder builder = attributes.toBuilder();
        labels.forEach(builder::put);
        return builder.build();
    }

    private static Stream<MetricPoint> timeStatSummaries(String metricName, TimeStat timeStat, Attributes attributes, String description)
    {
        return StatWindows.windows(timeStat).stream()
                .map(window -> timeSummary(metricName + "." + window.name(), window.value(), attributes, description));
    }

    private static MetricPoint timeSummary(String metricName, TimeDistribution timeDistribution, Attributes attributes, String description)
    {
        return new MetricPoint(new MetricFamilyKey(metricName, MetricKind.SUMMARY, timeUnit(timeDistribution.getUnit())), toSummary(timeDistribution), attributes, description);
    }

    private static String timeUnit(TimeUnit unit)
    {
        return switch (unit) {
            case NANOSECONDS -> NANOSECOND_UNIT;
            case MICROSECONDS -> "us";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "min";
            case HOURS -> "h";
            case DAYS -> "d";
        };
    }

    private static Stream<MetricPoint> distributionStatSummaries(String metricName, DistributionStat distributionStat, Attributes attributes, String description)
    {
        return StatWindows.windows(distributionStat).stream()
                .map(window -> new MetricPoint(new MetricFamilyKey(metricName + "." + window.name(), MetricKind.SUMMARY, ""), toSummary(window.value()), attributes, description));
    }

    private static SummaryValue toSummary(TimeDistribution timeDistribution)
    {
        TimeDistributionSnapshot snapshot = timeDistribution.snapshot();
        return new SummaryValue(
                (long) snapshot.count(),
                snapshot.count() == 0 ? 0 : snapshot.avg() * snapshot.count(),
                ImmutableList.of(
                        ValueAtQuantile.create(0.5, snapshot.p50()),
                        ValueAtQuantile.create(0.75, snapshot.p75()),
                        ValueAtQuantile.create(0.9, snapshot.p90()),
                        ValueAtQuantile.create(0.95, snapshot.p95()),
                        ValueAtQuantile.create(0.99, snapshot.p99())));
    }

    private static SummaryValue toSummary(Distribution distribution)
    {
        DistributionSnapshot snapshot = distribution.snapshot();
        return new SummaryValue(
                (long) snapshot.count(),
                snapshot.total(),
                ImmutableList.of(
                        ValueAtQuantile.create(0.01, snapshot.p01()),
                        ValueAtQuantile.create(0.05, snapshot.p05()),
                        ValueAtQuantile.create(0.10, snapshot.p10()),
                        ValueAtQuantile.create(0.25, snapshot.p25()),
                        ValueAtQuantile.create(0.5, snapshot.p50()),
                        ValueAtQuantile.create(0.75, snapshot.p75()),
                        ValueAtQuantile.create(0.9, snapshot.p90()),
                        ValueAtQuantile.create(0.95, snapshot.p95()),
                        ValueAtQuantile.create(0.99, snapshot.p99())));
    }

    private static Attributes attributes(Map<String, String> labels, Map<String, String> metricLabels)
    {
        if (labels.isEmpty() && metricLabels.isEmpty()) {
            return Attributes.empty();
        }

        AttributesBuilder builder = Attributes.builder();
        labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue()));
        metricLabels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue()));
        return builder.build();
    }

    private enum MetricKind
    {
        DOUBLE_GAUGE,
        LONG_SUM,
        SUMMARY,
        EXPONENTIAL_HISTOGRAM,
    }

    private interface MetricDataFactory<T>
    {
        MetricData create(Resource resource, InstrumentationScopeInfo scope, String metricName, String description, String unit, T data);
    }

    private record MetricFamilyKey(String name, MetricKind kind, String unit) {}

    private record MetricIdentity(String name, Map<String, String> labels) {}

    private record MetricPoint(MetricFamilyKey family, Object value, Attributes attributes, String description)
    {
        public String unit()
        {
            return family.unit();
        }
    }

    private record SummaryValue(long count, double sum, List<ValueAtQuantile> valuesAtQuantiles) {}

    record ConversionResult(List<MetricData> metricData, long droppedPoints) {}
}
